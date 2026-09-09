"""
Rule-based procurement recommendation (M10).

For each depot and each forecast week held out by M9, produces one
recommendation row:

    recommended_order_kl = max(0, forecast - opening_inventory + safety_stock)

Winner model is Ridge (M9 verified 5.27% MAPE vs seasonal-naive 28.12%).
Grade is ANHYDROUS (90% of dispatches; the grade OMCs actually blend).

Supplier chosen by cheapest median unit price over the last 4 weeks of
dispatched history, restricted to active suppliers with at least one
active plant. Ties broken by highest reliability_score.

Opening inventory is current operational.inventory (ANHYDROUS row per
depot). Static across the horizon - we don't simulate week-by-week
drawdown. Honest limitation, surfaced in rationale.

Persists to gold.recommendation_weekly (full refresh).
"""
from __future__ import annotations

import json
import logging

import pandas as pd
from sqlalchemy import text

from .forecast import build_engine

log = logging.getLogger(__name__)

TARGET_GRADE = "ANHYDROUS"
MODEL_SOURCE = "ridge"
SAFETY_STOCK_STD_MULTIPLIER = 2.0
SAFETY_STOCK_CAP_PCT = 0.30


RECOMMENDATION_DDL = """
CREATE TABLE IF NOT EXISTS gold.recommendation_weekly (
    depot_code           VARCHAR(50)   NOT NULL,
    week_start_date      DATE          NOT NULL,
    ethanol_grade        VARCHAR(20)   NOT NULL,
    forecast_kl          NUMERIC(15,3) NOT NULL,
    opening_inventory_kl NUMERIC(15,3) NOT NULL,
    safety_stock_kl      NUMERIC(15,3) NOT NULL,
    recommended_order_kl NUMERIC(15,3) NOT NULL,
    chosen_supplier_code VARCHAR(50),
    unit_price_inr_per_l NUMERIC(10,3),
    estimated_cost_inr   NUMERIC(18,2),
    model_source         VARCHAR(30)   NOT NULL,
    rationale            JSONB         NOT NULL,
    _built_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (depot_code, week_start_date, ethanol_grade)
);
CREATE INDEX IF NOT EXISTS ix_gold_reco_week  ON gold.recommendation_weekly (week_start_date);
CREATE INDEX IF NOT EXISTS ix_gold_reco_depot ON gold.recommendation_weekly (depot_code);
"""


def load_inputs(engine):
    with engine.connect() as conn:
        forecast = pd.read_sql(
            text(f"""
                SELECT depot_code, week_start_date, forecast_kl
                FROM gold.forecast_weekly
                WHERE model_name = '{MODEL_SOURCE}'
                ORDER BY depot_code, week_start_date
            """),
            conn, parse_dates=["week_start_date"],
        )
        history = pd.read_sql(
            text("""
                SELECT depot_code, ethanol_required_kl
                FROM gold.fact_weekly_ethanol_demand
            """),
            conn,
        )
        inventory = pd.read_sql(
            text(f"""
                SELECT d.depot_code, i.current_stock_kl, d.storage_capacity_kl
                FROM operational.inventory i
                JOIN operational.depots    d ON d.id = i.depot_id
                WHERE i.ethanol_grade = '{TARGET_GRADE}'
                  AND d.active = true
                  AND d.depot_code NOT LIKE '%TEST%'
            """),
            conn,
        )
        # Cheapest supplier by 4-week median dispatched price, per grade.
        # Uses SILVER (has business codes joined) rather than operational.
        supplier_prices = pd.read_sql(
            text(f"""
                WITH last4w AS (
                    SELECT s.supplier_code, s.unit_price_inr_per_l, s.quantity_kl
                    FROM silver.dispatch_orders_clean s
                    WHERE s.dq_valid
                      AND s.ethanol_grade = '{TARGET_GRADE}'
                      AND s.order_date >= (
                          SELECT MAX(order_date) - INTERVAL '28 days'
                          FROM silver.dispatch_orders_clean
                          WHERE ethanol_grade = '{TARGET_GRADE}'
                      )
                )
                SELECT
                    l.supplier_code,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY l.unit_price_inr_per_l) AS median_price,
                    SUM(l.quantity_kl) AS recent_volume_kl,
                    sup.reliability_score
                FROM last4w l
                JOIN operational.suppliers sup ON sup.supplier_code = l.supplier_code
                WHERE sup.active = true
                  AND sup.supplier_code NOT LIKE '%-999'
                  AND EXISTS (
                      SELECT 1 FROM operational.production_plants p
                      WHERE p.supplier_id = sup.id AND p.active = true
                  )
                GROUP BY l.supplier_code, sup.reliability_score
                ORDER BY median_price ASC, sup.reliability_score DESC NULLS LAST
            """),
            conn,
        )
    return forecast, history, inventory, supplier_prices


def compute_safety_stock(history: pd.DataFrame, depot_code: str,
                         storage_capacity_kl: float) -> float:
    depot_hist = history[history["depot_code"] == depot_code]["ethanol_required_kl"]
    if len(depot_hist) < 2:
        return 0.0
    std_based = float(depot_hist.std() * SAFETY_STOCK_STD_MULTIPLIER)
    cap = float(storage_capacity_kl) * SAFETY_STOCK_CAP_PCT
    return round(min(std_based, cap), 3)


def choose_supplier(supplier_prices: pd.DataFrame) -> dict | None:
    """Cheapest by median price, tie-break by highest reliability_score."""
    if supplier_prices.empty:
        return None
    top = supplier_prices.iloc[0]
    alternatives = supplier_prices.iloc[1:].head(3).to_dict(orient="records")
    return {
        "supplier_code": top["supplier_code"],
        "median_price": float(top["median_price"]),
        "recent_volume_kl": float(top["recent_volume_kl"] or 0.0),
        "reliability_score": (
            float(top["reliability_score"]) if pd.notna(top["reliability_score"]) else None
        ),
        "alternatives": [
            {
                "supplier_code": a["supplier_code"],
                "median_price": float(a["median_price"]),
            }
            for a in alternatives
        ],
    }


def build_recommendations() -> pd.DataFrame:
    engine = build_engine()
    with engine.begin() as conn:
        conn.execute(text(RECOMMENDATION_DDL))

    forecast, history, inventory, supplier_prices = load_inputs(engine)
    log.info("Forecast rows: %d, inventory depots: %d, supplier candidates: %d",
             len(forecast), len(inventory), len(supplier_prices))

    inv_by_depot = inventory.set_index("depot_code")
    supplier_pick = choose_supplier(supplier_prices)
    if supplier_pick is None:
        raise RuntimeError(
            "No eligible supplier found. Cannot generate recommendations."
        )

    rows = []
    for _, r in forecast.iterrows():
        depot = r["depot_code"]
        if depot not in inv_by_depot.index:
            log.warning("Depot %s not in operational.inventory; skipping.", depot)
            continue
        opening_inv = float(inv_by_depot.loc[depot, "current_stock_kl"])
        storage_cap = float(inv_by_depot.loc[depot, "storage_capacity_kl"])
        safety = compute_safety_stock(history, depot, storage_cap)
        forecast_kl = float(r["forecast_kl"])
        recommended = max(0.0, forecast_kl - opening_inv + safety)
        unit_price = supplier_pick["median_price"]
        est_cost = round(recommended * 1000.0 * unit_price, 2)

        rationale = {
            "formula": "max(0, forecast - opening_inventory + safety_stock)",
            "components": {
                "forecast_kl": round(forecast_kl, 3),
                "opening_inventory_kl": round(opening_inv, 3),
                "safety_stock_kl": safety,
            },
            "safety_stock": {
                "method": f"min({SAFETY_STOCK_STD_MULTIPLIER}*std, {int(SAFETY_STOCK_CAP_PCT*100)}%_of_capacity)",
                "storage_capacity_kl": storage_cap,
            },
            "supplier_choice": {
                "criterion": "cheapest median price over last 28 days, tie-break by reliability_score",
                "chosen": supplier_pick["supplier_code"],
                "chosen_median_price": supplier_pick["median_price"],
                "alternatives_considered": supplier_pick["alternatives"],
            },
            "model_source": MODEL_SOURCE,
            "limitations": [
                "Opening inventory is a static current snapshot; does not model week-by-week drawdown.",
                "Supplier price is a 28-day median; longer-term contracts not modelled.",
                "Grade is ANHYDROUS only; HYDROUS_95 / DENATURED are ~10% of dispatches, not recommended here.",
            ],
        }
        rows.append({
            "depot_code": depot,
            "week_start_date": r["week_start_date"].date() if hasattr(r["week_start_date"], "date") else r["week_start_date"],
            "ethanol_grade": TARGET_GRADE,
            "forecast_kl": round(forecast_kl, 3),
            "opening_inventory_kl": round(opening_inv, 3),
            "safety_stock_kl": safety,
            "recommended_order_kl": round(recommended, 3),
            "chosen_supplier_code": supplier_pick["supplier_code"],
            "unit_price_inr_per_l": unit_price,
            "estimated_cost_inr": est_cost,
            "model_source": MODEL_SOURCE,
            "rationale": json.dumps(rationale),
        })

    df = pd.DataFrame(rows)
    with engine.begin() as conn:
        conn.execute(text("TRUNCATE gold.recommendation_weekly"))
        conn.execute(
            text("""
                INSERT INTO gold.recommendation_weekly (
                    depot_code, week_start_date, ethanol_grade,
                    forecast_kl, opening_inventory_kl, safety_stock_kl,
                    recommended_order_kl, chosen_supplier_code,
                    unit_price_inr_per_l, estimated_cost_inr,
                    model_source, rationale
                ) VALUES (
                    :depot_code, :week_start_date, :ethanol_grade,
                    :forecast_kl, :opening_inventory_kl, :safety_stock_kl,
                    :recommended_order_kl, :chosen_supplier_code,
                    :unit_price_inr_per_l, :estimated_cost_inr,
                    :model_source, CAST(:rationale AS JSONB)
                )
            """),
            df.to_dict(orient="records"),
        )
    log.info("Persisted %d recommendations.", len(df))
    return df


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    df = build_recommendations()
    print("\n=== Recommendation summary (per depot) ===")
    summary = (
        df.groupby("depot_code")
          .agg(
              weeks=("week_start_date", "count"),
              avg_forecast_kl=("forecast_kl", "mean"),
              avg_recommended_kl=("recommended_order_kl", "mean"),
              total_estimated_cost_inr=("estimated_cost_inr", "sum"),
              chosen_supplier=("chosen_supplier_code", "first"),
          )
          .round(2)
    )
    print(summary.to_string())
    print(f"\nTotal rows: {len(df)}")
