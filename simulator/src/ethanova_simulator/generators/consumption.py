"""Weekly petrol dispensing and ethanol blending generator per depot."""

from __future__ import annotations

import math
from datetime import date, timedelta

import numpy as np
import pandas as pd

from ..config import (
    BLEND_END_PCT,
    BLEND_START_PCT,
    DEPOT_BASE_PETROL_KL_PER_WEEK,
    FESTIVAL_MULTIPLIERS,
    NOISE_SIGMA,
    SEASONAL_AMPLITUDE_PCT,
    SEASONAL_PEAK_ISO_WEEK,
    SECULAR_ANNUAL_GROWTH_PCT,
    SHORTFALL_PROBABILITY,
    SHORTFALL_RANGE,
)


# Festival date approximations covering the two years the simulator spans.
# Not astronomically precise - good enough to inject small demand bumps.
_FESTIVAL_DATES: dict[date, str] = {
    # Diwali
    date(2024, 11, 1):  "diwali",
    date(2025, 10, 21): "diwali",
    date(2026, 11, 8):  "diwali",
    # Holi
    date(2024, 3, 25):  "holi",
    date(2025, 3, 14):  "holi",
    date(2026, 3, 3):   "holi",
    # Eid al-Fitr
    date(2024, 4, 10):  "eid",
    date(2025, 3, 31):  "eid",
    date(2026, 3, 20):  "eid",
}


def build_week_index(num_weeks: int, end_week_start: date) -> list[date]:
    """Return `num_weeks` Monday dates in chronological order, ending at `end_week_start`."""
    return [end_week_start - timedelta(weeks=i) for i in range(num_weeks - 1, -1, -1)]


def _seasonal_multiplier(iso_week: int) -> float:
    phase = 2 * math.pi * (iso_week - SEASONAL_PEAK_ISO_WEEK) / 52.0
    return 1.0 + (SEASONAL_AMPLITUDE_PCT / 100.0) * math.cos(phase)


def _festival_multiplier(week_start: date) -> float:
    mult = 1.0
    for offset in range(7):
        d = week_start + timedelta(days=offset)
        name = _FESTIVAL_DATES.get(d)
        if name is not None:
            mult *= FESTIVAL_MULTIPLIERS[name]
    return mult


def _blend_pct(week_index: int, total_weeks: int) -> float:
    """Linear ramp from BLEND_START_PCT to BLEND_END_PCT over the window."""
    if total_weeks <= 1:
        return BLEND_END_PCT
    frac = week_index / (total_weeks - 1)
    return BLEND_START_PCT + (BLEND_END_PCT - BLEND_START_PCT) * frac


def generate_consumption(
    depots: pd.DataFrame,
    week_starts: list[date],
    rng: np.random.Generator,
) -> pd.DataFrame:
    """Generate one row per (depot, week).

    Returns a DataFrame with columns matching `operational.depot_weekly_consumption`.
    """
    total_weeks = len(week_starts)
    rows: list[dict] = []
    for _, depot in depots.iterrows():
        code = depot["depot_code"]
        base = DEPOT_BASE_PETROL_KL_PER_WEEK.get(code)
        if base is None:
            # Fallback for depots not in the config map: 2x storage capacity
            base = float(depot["storage_capacity_kl"]) * 2.0
        for wi, week_start in enumerate(week_starts):
            iso_year, iso_week, _ = week_start.isocalendar()
            trend = 1.0 + (SECULAR_ANNUAL_GROWTH_PCT / 100.0) * (wi / 52.0)
            seasonal = _seasonal_multiplier(iso_week)
            festival = _festival_multiplier(week_start)
            noise = float(rng.lognormal(mean=0.0, sigma=NOISE_SIGMA))
            petrol_kl = base * trend * seasonal * festival * noise

            blend_pct = _blend_pct(wi, total_weeks)
            ethanol_required = petrol_kl * blend_pct / 100.0

            if rng.random() < SHORTFALL_PROBABILITY:
                shortfall = float(rng.uniform(*SHORTFALL_RANGE))
            else:
                shortfall = 0.0
            ethanol_blended = ethanol_required * (1.0 - shortfall)

            rows.append({
                "depot_id":                int(depot["id"]),
                "week_start_date":         week_start,
                "iso_year":                int(iso_year),
                "iso_week":                int(iso_week),
                "petrol_dispensed_kl":     round(petrol_kl, 3),
                "target_blend_percentage": round(blend_pct, 2),
                "ethanol_required_kl":     round(ethanol_required, 3),
                "ethanol_blended_kl":      round(ethanol_blended, 3),
                "data_source":             "SIMULATOR",
            })
    return pd.DataFrame(rows)