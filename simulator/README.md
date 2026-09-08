# Ethanova Simulator

Historical data generator for the Ethanova E20 supply chain platform.

Generates deterministic weekly petrol dispensing, ethanol blending, and dispatch
order history for reference depots and suppliers. Writes directly to the
`operational` schema via SQLAlchemy — **not** through the REST API. Historical
seeding is a separate concern from the live write path.

## Setup

    cd simulator
    py -3.11 -m venv .venv
    .\.venv\Scripts\Activate.ps1
    pip install -e ".[dev]"

## Generate 104 weeks of history

    python -m ethanova_simulator generate --weeks 104 --seed 42 --truncate

Add `--sanity-plot` to write per-depot matplotlib PNGs to `verification/`.

## Model

- **Consumption:** weekly petrol dispensed per depot with ~3% secular growth,
  ±12% seasonal swing peaking around ISO week 44 (early November), festival
  bumps for Diwali/Holi/Eid, and ~6% lognormal noise.
- **Blend policy:** linear ramp from 10% to 20% over the window (E20 rollout).
- **Supply shortfalls:** ~3% of weeks see 5–15% ethanol shortfall
  (blended < required). Silver/Gold layers can flag these.
- **Dispatch orders:** 2–3 per depot per week, allocated across suppliers
  weighted by reliability score (uniform fallback when scores are null).
  Grades: 90% ANHYDROUS / 7% HYDROUS_95 / 3% DENATURED. Prices ramp from
  ~62.500 to ~72.000 INR/L with small noise. All historical orders are DELIVERED.

## What this simulator does NOT do

- No writes to `suppliers`, `production_plants`, `depots`, `users`, `inventory`.
- No round-trip through the REST API for historical seeding.
- No use of Flyway or JPA. Reference data ownership stays with the backend.

## Tests

    py -3.11 -m pytest simulator/tests