"""Configuration for the Ethanova simulator.

Reads database credentials from `deployment/.env` (or process environment) and
holds tunable constants for the generation model. Keeping all model constants
in one file makes them auditable and easy to trace in a viva.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


# simulator/src/ethanova_simulator/config.py -> parents[3] = repo root
REPO_ROOT = Path(__file__).resolve().parents[3]
ENV_FILE = REPO_ROOT / "deployment" / ".env"


def _load_env() -> None:
    if ENV_FILE.exists():
        load_dotenv(ENV_FILE, override=False)


@dataclass(frozen=True)
class DbConfig:
    user: str
    password: str
    database: str
    host: str
    port: int

    @property
    def url(self) -> str:
        return (
            f"postgresql+psycopg2://{self.user}:{self.password}"
            f"@{self.host}:{self.port}/{self.database}"
        )

    @classmethod
    def from_env(cls) -> "DbConfig":
        _load_env()
        try:
            return cls(
                user=os.environ["POSTGRES_USER"],
                password=os.environ["POSTGRES_PASSWORD"],
                database=os.environ["POSTGRES_DB"],
                host=os.environ.get("SIMULATOR_DB_HOST", "localhost"),
                port=int(os.environ.get("SIMULATOR_DB_PORT", "5432")),
            )
        except KeyError as e:
            raise RuntimeError(
                f"Missing required env var {e.args[0]}. "
                f"Expected in {ENV_FILE} or process environment."
            ) from None


# --- Generation model constants ---------------------------------------------

# Depot base weekly petrol dispensing (KL) at year 1 baseline.
# Illustrative levels roughly 2x depot storage capacity; tunable.
DEPOT_BASE_PETROL_KL_PER_WEEK: dict[str, float] = {
    "DEP-UP-KNP": 10000.0,
    "DEP-UP-LKO":  9000.0,
    "DEP-UP-AGR":  8500.0,
    "DEP-UP-VNS":  7500.0,
    "DEP-UP-MDB":  7000.0,
}

# Blend policy: linear ramp from BLEND_START_PCT (week 0) to BLEND_END_PCT (last week).
BLEND_START_PCT: float = 10.0
BLEND_END_PCT: float = 20.0

# Petrol demand model
SECULAR_ANNUAL_GROWTH_PCT: float = 3.0     # ~3% per year secular growth
SEASONAL_AMPLITUDE_PCT: float = 12.0        # +/- 12% seasonal swing
SEASONAL_PEAK_ISO_WEEK: int = 44            # early November peak
NOISE_SIGMA: float = 0.06                   # ~6% weekly lognormal noise

# Festival multipliers, applied when a week contains the anchor date
FESTIVAL_MULTIPLIERS: dict[str, float] = {
    "diwali": 1.18,
    "holi":   1.10,
    "eid":    1.08,
}

# Supply shortfall model
SHORTFALL_PROBABILITY: float = 0.03
SHORTFALL_RANGE: tuple[float, float] = (0.05, 0.15)

# Dispatch model
DISPATCH_BUFFER_FACTOR: float = 1.02        # dispatched ~ blended * 1.02
GRADE_WEIGHTS: dict[str, float] = {
    "ANHYDROUS":  0.90,
    "HYDROUS_95": 0.07,
    "DENATURED":  0.03,
}
UNIT_PRICE_START_INR_PER_L: float = 62.500
UNIT_PRICE_END_INR_PER_L:   float = 72.000
UNIT_PRICE_NOISE_SIGMA:     float = 0.015

# Reference-data filters - exclude test/fixture rows created via REST testing
EXCLUDED_CODE_PATTERNS: tuple[str, ...] = ("%TEST%", "%-999")