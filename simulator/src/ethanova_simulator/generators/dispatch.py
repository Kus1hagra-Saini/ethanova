"""Weekly dispatch order generator."""

from __future__ import annotations

from collections import defaultdict
from datetime import date, datetime, timedelta, timezone

import numpy as np
import pandas as pd

from ..config import (
    DISPATCH_BUFFER_FACTOR,
    GRADE_WEIGHTS,
    UNIT_PRICE_END_INR_PER_L,
    UNIT_PRICE_NOISE_SIGMA,
    UNIT_PRICE_START_INR_PER_L,
)


def _n_orders_for_depot(base_petrol_kl: float, rng: np.random.Generator) -> int:
    """Larger depots tend to get 3 orders/week, smaller depots 2."""
    if base_petrol_kl >= 8000:
        return 3 if rng.random() < 0.7 else 2
    return 2 if rng.random() < 0.8 else 3


def _supplier_weights(suppliers: pd.DataFrame) -> np.ndarray:
    """Weight suppliers by reliability_score; fall back to uniform if all null."""
    scores = suppliers["reliability_score"].to_numpy(dtype=float)
    if np.all(np.isnan(scores)):
        return np.ones(len(scores)) / len(scores)
    filled = np.where(np.isnan(scores), np.nanmean(scores), scores)
    return filled / filled.sum()


def _unit_price(week_index: int, total_weeks: int, rng: np.random.Generator) -> float:
    if total_weeks <= 1:
        base = UNIT_PRICE_END_INR_PER_L
    else:
        frac = week_index / (total_weeks - 1)
        base = UNIT_PRICE_START_INR_PER_L + (
            UNIT_PRICE_END_INR_PER_L - UNIT_PRICE_START_INR_PER_L
        ) * frac
    noise = float(rng.normal(0.0, UNIT_PRICE_NOISE_SIGMA))
    return round(base * (1.0 + noise), 3)


def generate_dispatch_orders(
    consumption: pd.DataFrame,
    suppliers: pd.DataFrame,
    plants: pd.DataFrame,
    depots: pd.DataFrame,
    rng: np.random.Generator,
) -> pd.DataFrame:
    """Generate ~2-3 dispatch orders per (depot, week)."""
    total_weeks = int(consumption["week_start_date"].nunique())
    week_dates_sorted = sorted(pd.to_datetime(consumption["week_start_date"]).dt.date.unique())
    week_index_by_date: dict[date, int] = {wk: i for i, wk in enumerate(week_dates_sorted)}

    depot_base_by_id: dict[int, float] = {
        int(d["id"]): float(d["storage_capacity_kl"]) * 2.0
        for _, d in depots.iterrows()
    }

    plants_by_supplier: dict[int, list[int]] = defaultdict(list)
    for _, p in plants.iterrows():
        plants_by_supplier[int(p["supplier_id"])].append(int(p["id"]))

    supplier_ids = suppliers["id"].to_numpy(dtype=int)
    weights = _supplier_weights(suppliers)
    grades = list(GRADE_WEIGHTS.keys())
    grade_probs = np.array(list(GRADE_WEIGHTS.values()), dtype=float)
    grade_probs = grade_probs / grade_probs.sum()

    per_month_counter: dict[tuple[int, int], int] = defaultdict(int)
    rows: list[dict] = []

    consumption_sorted = consumption.sort_values(["week_start_date", "depot_id"])
    for _, cons in consumption_sorted.iterrows():
        depot_id = int(cons["depot_id"])
        raw_week = cons["week_start_date"]
        week_start: date = raw_week if isinstance(raw_week, date) else pd.Timestamp(raw_week).date()
        wi = week_index_by_date[week_start]
        base = depot_base_by_id[depot_id]
        blended_kl = float(cons["ethanol_blended_kl"])
        if blended_kl <= 0:
            continue
        total_dispatched = blended_kl * DISPATCH_BUFFER_FACTOR

        n_orders = _n_orders_for_depot(base, rng)
        splits = rng.dirichlet(np.ones(n_orders) * 3.0)  # simplex, no rounding drift
        quantities = total_dispatched * splits

        chosen_supplier_ids = rng.choice(
            supplier_ids, size=n_orders, replace=True, p=weights,
        )
        chosen_grades = rng.choice(grades, size=n_orders, replace=True, p=grade_probs)

        for i in range(n_orders):
            supplier_id = int(chosen_supplier_ids[i])
            plant_options = plants_by_supplier.get(supplier_id, [])
            if not plant_options:
                continue  # supplier has no active plants after filtering
            plant_id = plant_options[int(rng.integers(0, len(plant_options)))]

            quantity_kl = round(float(quantities[i]), 3)
            if quantity_kl <= 0:
                continue

            unit_price = _unit_price(wi, total_weeks, rng)
            total_amount = round(quantity_kl * 1000.0 * unit_price, 2)

            year, month = week_start.year, week_start.month
            per_month_counter[(year, month)] += 1
            seq = per_month_counter[(year, month)]
            order_number = f"DO-{year:04d}-{month:02d}-{seq:04d}"

            expected_arrival = week_start + timedelta(days=3)
            offset_hours = int(rng.integers(-6, 30))
            actual_arrival_at = (
                datetime.combine(expected_arrival, datetime.min.time(), tzinfo=timezone.utc)
                + timedelta(hours=offset_hours)
            )

            rows.append({
                "order_number":          order_number,
                "supplier_id":           supplier_id,
                "source_plant_id":       plant_id,
                "destination_depot_id":  depot_id,
                "ethanol_grade":         str(chosen_grades[i]),
                "quantity_kl":           quantity_kl,
                "unit_price_inr_per_l":  unit_price,
                "total_amount_inr":      total_amount,
                "order_date":            week_start,
                "expected_arrival_date": expected_arrival,
                "actual_arrival_at":     actual_arrival_at,
                "status":                "DELIVERED",
            })
    return pd.DataFrame(rows)