"""Simulator CLI entry point.

Usage:
    python -m ethanova_simulator generate [--weeks 104] [--seed 42]
                                          [--end-week YYYY-MM-DD] [--truncate]
                                          [--sanity-plot]
"""

from __future__ import annotations

import argparse
import sys
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd
from sqlalchemy import text
from sqlalchemy.engine import Engine

from .config import REPO_ROOT
from .db import build_engine, load_reference_data, truncate_generated_tables
from .generators.consumption import build_week_index, generate_consumption
from .generators.dispatch import generate_dispatch_orders


CONSUMPTION_COLS = [
    "depot_id", "week_start_date", "iso_year", "iso_week",
    "petrol_dispensed_kl", "target_blend_percentage",
    "ethanol_required_kl", "ethanol_blended_kl", "data_source",
]

DISPATCH_COLS = [
    "order_number", "supplier_id", "source_plant_id", "destination_depot_id",
    "ethanol_grade", "quantity_kl", "unit_price_inr_per_l", "total_amount_inr",
    "order_date", "expected_arrival_date", "actual_arrival_at", "status",
]


def _bulk_insert(engine: Engine, table: str, df: pd.DataFrame, cols: list[str]) -> int:
    if df.empty:
        return 0
    payload = df[cols].to_dict(orient="records")
    placeholders = ", ".join(f":{c}" for c in cols)
    col_list = ", ".join(cols)
    stmt = text(f"INSERT INTO operational.{table} ({col_list}) VALUES ({placeholders})")
    with engine.begin() as conn:
        conn.execute(stmt, payload)
    return len(payload)


def _sanity_plot(consumption: pd.DataFrame, depots: pd.DataFrame, out_dir: Path) -> None:
    try:
        import matplotlib.pyplot as plt  # local import - dev extra
    except ImportError:
        print("[sanity-plot] matplotlib not installed; skipping.", file=sys.stderr)
        return
    out_dir.mkdir(parents=True, exist_ok=True)
    for _, d in depots.iterrows():
        df = consumption[consumption["depot_id"] == d["id"]].sort_values("week_start_date")
        fig, ax = plt.subplots(figsize=(10, 4))
        ax.plot(df["week_start_date"], df["petrol_dispensed_kl"], label="Petrol dispensed (KL)")
        ax.plot(df["week_start_date"], df["ethanol_required_kl"] * 10.0,
                label="Ethanol required x 10 (KL)")
        ax.set_title(f"{d['depot_code']} - weekly demand")
        ax.set_xlabel("Week")
        ax.set_ylabel("KL")
        ax.legend()
        fig.autofmt_xdate()
        fig.tight_layout()
        out_path = out_dir / f"{d['depot_code']}.png"
        fig.savefig(out_path, dpi=100)
        plt.close(fig)
        print(f"[sanity-plot] wrote {out_path}")


def _resolve_end_week(raw: str | None) -> date:
    if raw is None:
        today = date.today()
        return today - timedelta(days=today.weekday())
    return date.fromisoformat(raw)


def _cmd_generate(args: argparse.Namespace) -> int:
    end_week = _resolve_end_week(args.end_week)
    rng = np.random.default_rng(args.seed)
    engine = build_engine()

    ref = load_reference_data(engine)
    print(
        f"Loaded reference data: {len(ref.depots)} depots, "
        f"{len(ref.suppliers)} suppliers, {len(ref.plants)} plants"
    )
    if ref.depots.empty or ref.suppliers.empty or ref.plants.empty:
        print("ERROR: reference data missing rows after filtering - aborting.", file=sys.stderr)
        return 2

    week_starts = build_week_index(args.weeks, end_week)
    print(
        f"Generating {args.weeks} weeks: "
        f"{week_starts[0].isoformat()} -> {week_starts[-1].isoformat()}"
    )

    consumption = generate_consumption(ref.depots, week_starts, rng)
    dispatch = generate_dispatch_orders(
        consumption, ref.suppliers, ref.plants, ref.depots, rng
    )
    print(
        f"Generated in memory: {len(consumption)} consumption rows, "
        f"{len(dispatch)} dispatch orders"
    )

    if args.truncate:
        truncate_generated_tables(engine)
        print("Truncated depot_weekly_consumption and dispatch_orders")

    n_cons = _bulk_insert(engine, "depot_weekly_consumption", consumption, CONSUMPTION_COLS)
    n_disp = _bulk_insert(engine, "dispatch_orders", dispatch, DISPATCH_COLS)
    print(f"Inserted: {n_cons} consumption rows, {n_disp} dispatch orders")

    if args.sanity_plot:
        _sanity_plot(consumption, ref.depots, REPO_ROOT / "simulator" / "verification")

    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="ethanova-simulator")
    sub = parser.add_subparsers(dest="command", required=True)

    gen = sub.add_parser("generate", help="Generate historical simulator data")
    gen.add_argument("--weeks", type=int, default=104,
                     help="Number of weeks of history (default: 104)")
    gen.add_argument("--seed", type=int, default=42,
                     help="Deterministic RNG seed (default: 42)")
    gen.add_argument("--end-week", type=str, default=None,
                     help="Last week's Monday date (YYYY-MM-DD). "
                          "Defaults to the Monday of the current week.")
    gen.add_argument("--truncate", action="store_true",
                     help="Delete existing rows in target tables before insert")
    gen.add_argument("--sanity-plot", action="store_true",
                     help="Write per-depot matplotlib PNGs to simulator/verification/")

    args = parser.parse_args(argv)
    if args.command == "generate":
        return _cmd_generate(args)
    return 1


if __name__ == "__main__":
    sys.exit(main())