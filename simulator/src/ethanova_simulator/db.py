"""Database access for the simulator.

Two responsibilities:
  1. Provide a SQLAlchemy engine bound to the operational database.
  2. Load reference data (depots, suppliers, plants) with test rows filtered.
"""

from __future__ import annotations

from dataclasses import dataclass

import pandas as pd
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from .config import DbConfig, EXCLUDED_CODE_PATTERNS


@dataclass(frozen=True)
class ReferenceData:
    depots: pd.DataFrame       # id, depot_code, storage_capacity_kl
    suppliers: pd.DataFrame    # id, supplier_code, reliability_score
    plants: pd.DataFrame       # id, plant_code, supplier_id


def build_engine(config: DbConfig | None = None) -> Engine:
    cfg = config or DbConfig.from_env()
    return create_engine(cfg.url, future=True, pool_pre_ping=True)


def _exclude_clause(column: str) -> str:
    parts = [f"{column} NOT LIKE '{p}'" for p in EXCLUDED_CODE_PATTERNS]
    return " AND ".join(parts)


def load_reference_data(engine: Engine) -> ReferenceData:
    """Load active, non-test reference rows only.

    Suppliers are filtered to those with at least one active production plant -
    a supplier without a plant cannot fulfil a dispatch order.
    """
    depot_sql = text(
        f"""
        SELECT id, depot_code, storage_capacity_kl
        FROM operational.depots
        WHERE active = true AND {_exclude_clause('depot_code')}
        ORDER BY depot_code
        """
    )
    supplier_sql = text(
        f"""
        SELECT s.id, s.supplier_code, s.reliability_score
        FROM operational.suppliers s
        WHERE s.active = true
          AND {_exclude_clause('s.supplier_code')}
          AND EXISTS (
              SELECT 1 FROM operational.production_plants p
              WHERE p.supplier_id = s.id AND p.active = true
          )
        ORDER BY s.supplier_code
        """
    )
    plant_sql = text(
        f"""
        SELECT id, plant_code, supplier_id
        FROM operational.production_plants
        WHERE active = true AND {_exclude_clause('plant_code')}
        ORDER BY plant_code
        """
    )
    with engine.connect() as conn:
        depots = pd.read_sql(depot_sql, conn)
        suppliers = pd.read_sql(supplier_sql, conn)
        plants = pd.read_sql(plant_sql, conn)
    return ReferenceData(depots=depots, suppliers=suppliers, plants=plants)


def truncate_generated_tables(engine: Engine) -> None:
    """Delete all rows from tables the simulator owns.

    dispatch_orders is deleted first (FKs point outward to suppliers/plants/depots
    which the simulator does NOT touch).
    """
    with engine.begin() as conn:
        conn.execute(text("DELETE FROM operational.dispatch_orders"))
        conn.execute(text("DELETE FROM operational.depot_weekly_consumption"))