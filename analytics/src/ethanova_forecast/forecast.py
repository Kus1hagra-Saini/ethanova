"""
Baseline weekly ethanol demand forecast (M9).

Two models evaluated against the last HOLDOUT_WEEKS observations per depot:

  1. seasonal_naive - per-depot forecast = actual value 52 weeks prior.
     Zero training; defensible baseline (ADR #15, depth over breadth).

  2. ridge - ONE global Ridge regressor across all depots. Depot identity
     enters as one-hot features so a single model can share the seasonal
     shape while learning per-depot intercepts. Chosen over per-depot
     models because the dataset is tiny (~500 rows) and pooled fit is
     more stable, simpler to maintain, and easier to defend at viva.

Features for ridge:
  lag_1, lag_2, lag_4, lag_52   (kL)
  woy_sin, woy_cos              (ISO week seasonality)
  target_blend_percentage       (policy driver of demand)
  depot_DEP-* one-hots          (per-depot intercepts)

Persists forecasts to gold.forecast_weekly (full refresh, PK
depot+week+model).
"""
from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd
from dotenv import load_dotenv
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

log = logging.getLogger(__name__)

HOLDOUT_WEEKS = 8
LAGS = (1, 2, 4, 52)
RIDGE_ALPHA = 1.0
TARGET_COL = "ethanol_required_kl"

REPO_ROOT = Path(__file__).resolve().parents[3]
ENV_FILE = REPO_ROOT / "deployment" / ".env"


def build_engine() -> Engine:
    if ENV_FILE.exists():
        load_dotenv(ENV_FILE, override=False)
    user = os.environ["POSTGRES_USER"]
    password = os.environ["POSTGRES_PASSWORD"]
    database = os.environ["POSTGRES_DB"]
    host = os.environ.get("ANALYTICS_DB_HOST", "localhost")
    port = int(os.environ.get("ANALYTICS_DB_PORT", "5432"))
    url = f"postgresql+psycopg2://{user}:{password}@{host}:{port}/{database}"
    return create_engine(url, future=True, pool_pre_ping=True)


FORECAST_TABLE_DDL = """
CREATE TABLE IF NOT EXISTS gold.forecast_weekly (
    depot_code       VARCHAR(50)   NOT NULL,
    week_start_date  DATE          NOT NULL,
    model_name       VARCHAR(30)   NOT NULL,
    actual_kl        NUMERIC(15,3),
    forecast_kl      NUMERIC(15,3) NOT NULL,
    error_kl         NUMERIC(15,3),
    _built_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (depot_code, week_start_date, model_name)
);
"""


def load_demand(engine: Engine) -> pd.DataFrame:
    sql = text(
        """
        SELECT depot_code, week_start_date, iso_year, iso_week,
               target_blend_percentage, ethanol_required_kl
        FROM gold.fact_weekly_ethanol_demand
        ORDER BY depot_code, week_start_date
        """
    )
    with engine.connect() as conn:
        df = pd.read_sql(sql, conn, parse_dates=["week_start_date"])
    return df


def add_features(df: pd.DataFrame) -> pd.DataFrame:
    """Lag features + week-of-year sin/cos + depot one-hots."""
    out = df.copy()
    grp = out.groupby("depot_code", sort=False)[TARGET_COL]
    for lag in LAGS:
        out[f"lag_{lag}"] = grp.shift(lag)
    week_frac = 2 * np.pi * (out["iso_week"] - 1) / 52.0
    out["woy_sin"] = np.sin(week_frac)
    out["woy_cos"] = np.cos(week_frac)
    depot_dummies = pd.get_dummies(out["depot_code"], prefix="depot", dtype=float)
    out = pd.concat([out, depot_dummies], axis=1)
    return out


FEATURE_COLS_BASE = [f"lag_{lag}" for lag in LAGS] + [
    "woy_sin", "woy_cos", "target_blend_percentage"
]


def train_test_split(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Hold out the last HOLDOUT_WEEKS per depot as the test set."""
    df = df.sort_values(["depot_code", "week_start_date"]).reset_index(drop=True)
    df["_row_from_end"] = df.groupby("depot_code").cumcount(ascending=False)
    test  = df[df["_row_from_end"] < HOLDOUT_WEEKS].drop(columns="_row_from_end")
    train = df[df["_row_from_end"] >= HOLDOUT_WEEKS].drop(columns="_row_from_end")
    return train.reset_index(drop=True), test.reset_index(drop=True)


@dataclass
class ForecastResult:
    frame: pd.DataFrame
    metrics: pd.DataFrame


def forecast_seasonal_naive(demand: pd.DataFrame, test: pd.DataFrame) -> pd.DataFrame:
    """Per-depot forecast = value 52 weeks prior.

    With 104 weeks history and an 8-week holdout, every test observation has
    a valid t-52 lookup. Depot-median fallback retained defensively.
    """
    demand_indexed = demand.set_index(["depot_code", "week_start_date"])[TARGET_COL]
    rows = []
    for _, row in test.iterrows():
        depot = row["depot_code"]
        wk = row["week_start_date"]
        lag_wk = wk - pd.Timedelta(weeks=52)
        forecast = demand_indexed.get((depot, lag_wk))
        if forecast is None or pd.isna(forecast):
            forecast = demand[demand["depot_code"] == depot][TARGET_COL].median()
        rows.append({
            "depot_code": depot,
            "week_start_date": wk,
            "model_name": "seasonal_naive",
            "actual_kl": row[TARGET_COL],
            "forecast_kl": float(forecast),
        })
    out = pd.DataFrame(rows)
    out["error_kl"] = out["actual_kl"] - out["forecast_kl"]
    return out


def forecast_ridge(demand_feat: pd.DataFrame, train: pd.DataFrame,
                   test: pd.DataFrame) -> pd.DataFrame:
    """One global Ridge model. Depot enters as one-hot features."""
    depot_cols = [c for c in demand_feat.columns if c.startswith("depot_DEP-")]
    feature_cols = FEATURE_COLS_BASE + depot_cols

    train_ready = train.dropna(subset=feature_cols + [TARGET_COL])
    if train_ready.empty:
        raise ValueError("No training rows after dropping NaN lag features.")

    model = Ridge(alpha=RIDGE_ALPHA)
    model.fit(train_ready[feature_cols], train_ready[TARGET_COL])

    test_ready = test.dropna(subset=feature_cols).copy()
    test_ready["forecast_kl"] = model.predict(test_ready[feature_cols])
    test_ready["model_name"] = "ridge"
    test_ready["actual_kl"] = test_ready[TARGET_COL]
    test_ready["error_kl"] = test_ready["actual_kl"] - test_ready["forecast_kl"]
    return test_ready[
        ["depot_code", "week_start_date", "model_name",
         "actual_kl", "forecast_kl", "error_kl"]
    ].reset_index(drop=True)


def compute_metrics(frame: pd.DataFrame) -> pd.DataFrame:
    def _row(g: pd.DataFrame) -> pd.Series:
        actual = g["actual_kl"].to_numpy()
        pred = g["forecast_kl"].to_numpy()
        mae = mean_absolute_error(actual, pred)
        rmse = float(np.sqrt(mean_squared_error(actual, pred)))
        mape = float(np.mean(np.abs((actual - pred) / actual)) * 100.0)
        return pd.Series({"mae_kl": mae, "mape_pct": mape, "rmse_kl": rmse})
    return (
        frame.groupby(["depot_code", "model_name"], sort=True)
             .apply(_row, include_groups=False)
             .reset_index()
    )


def run() -> ForecastResult:
    engine = build_engine()
    with engine.begin() as conn:
        conn.execute(text(FORECAST_TABLE_DDL))

    demand = load_demand(engine)
    log.info("Loaded %d rows across %d depots.",
             len(demand), demand["depot_code"].nunique())

    demand_feat = add_features(demand)
    train, test = train_test_split(demand_feat)
    log.info("Train rows: %d, test rows: %d (%d weeks holdout per depot).",
             len(train), len(test), HOLDOUT_WEEKS)

    seasonal_naive_df = forecast_seasonal_naive(demand, test)
    ridge_df          = forecast_ridge(demand_feat, train, test)
    frame = pd.concat([seasonal_naive_df, ridge_df], ignore_index=True)
    metrics = compute_metrics(frame)

    with engine.begin() as conn:
        conn.execute(text("TRUNCATE gold.forecast_weekly"))
        conn.execute(
            text(
                """
                INSERT INTO gold.forecast_weekly
                    (depot_code, week_start_date, model_name,
                     actual_kl, forecast_kl, error_kl)
                VALUES (:depot_code, :week_start_date, :model_name,
                        :actual_kl, :forecast_kl, :error_kl)
                """
            ),
            frame.to_dict(orient="records"),
        )
    log.info("Persisted %d forecast rows to gold.forecast_weekly.", len(frame))
    return ForecastResult(frame=frame, metrics=metrics)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    result = run()
    print("\n=== Metrics per depot per model ===")
    print(result.metrics.to_string(index=False))
    print("\n=== Overall (mean across depots) ===")
    print(
        result.metrics
        .groupby("model_name", sort=True)[["mae_kl", "mape_pct", "rmse_kl"]]
        .mean()
        .round(2)
        .to_string()
    )
