"""Smoke tests for the simulator generators.

Pure logic only - no database access. Run with:
    py -3.11 -m pytest simulator/tests
"""

from __future__ import annotations

from datetime import date

import numpy as np
import pandas as pd
import pytest

from ethanova_simulator.generators.consumption import (
    build_week_index,
    generate_consumption,
)
from ethanova_simulator.generators.dispatch import generate_dispatch_orders


@pytest.fixture
def depots_df() -> pd.DataFrame:
    return pd.DataFrame([
        {"id": 1, "depot_code": "DEP-UP-KNP", "storage_capacity_kl": 5000.0},
        {"id": 2, "depot_code": "DEP-UP-LKO", "storage_capacity_kl": 4500.0},
    ])


@pytest.fixture
def suppliers_df() -> pd.DataFrame:
    return pd.DataFrame([
        {"id": 10, "supplier_code": "SUP-UP-001", "reliability_score": None},
        {"id": 11, "supplier_code": "SUP-UP-002", "reliability_score": None},
    ])


@pytest.fixture
def plants_df() -> pd.DataFrame:
    return pd.DataFrame([
        {"id": 100, "plant_code": "PLT-UP-001", "supplier_id": 10},
        {"id": 101, "plant_code": "PLT-UP-002", "supplier_id": 11},
    ])


def test_consumption_deterministic(depots_df):
    week_starts = build_week_index(12, date(2026, 8, 31))
    df1 = generate_consumption(depots_df, week_starts, np.random.default_rng(42))
    df2 = generate_consumption(depots_df, week_starts, np.random.default_rng(42))
    pd.testing.assert_frame_equal(df1, df2)


def test_consumption_shape_and_ranges(depots_df):
    week_starts = build_week_index(104, date(2026, 8, 31))
    df = generate_consumption(depots_df, week_starts, np.random.default_rng(42))
    assert len(df) == 104 * len(depots_df)
    assert df["petrol_dispensed_kl"].min() > 0
    assert df["ethanol_required_kl"].min() > 0
    assert df["ethanol_blended_kl"].min() >= 0
    assert df["target_blend_percentage"].between(9.9, 20.1).all()
    assert df.isnull().sum().sum() == 0


def test_dispatch_orders_generated(depots_df, suppliers_df, plants_df):
    week_starts = build_week_index(12, date(2026, 8, 31))
    rng = np.random.default_rng(42)
    consumption = generate_consumption(depots_df, week_starts, rng)
    dispatch = generate_dispatch_orders(consumption, suppliers_df, plants_df, depots_df, rng)
    assert len(dispatch) >= 12 * len(depots_df) * 2
    assert dispatch["order_number"].is_unique
    assert (dispatch["quantity_kl"] > 0).all()
    assert (dispatch["total_amount_inr"] > 0).all()
    assert set(dispatch["ethanol_grade"].unique()).issubset(
        {"ANHYDROUS", "HYDROUS_95", "DENATURED"}
    )
    assert (dispatch["status"] == "DELIVERED").all()