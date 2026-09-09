"""
Silver transform DAG — bronze -> silver schema.

Two output tables:
  silver.dispatch_orders_clean : deduped dispatch orders + joined dim codes
  silver.depot_weekly_demand   : deduped weekly consumption + DQ flags

Dedup: SELECT DISTINCT ON (business_key) ORDER BY key, updated_at DESC,
_ingested_at DESC. The _ingested_at tie-breaker makes the dedup fully
deterministic when updated_at collides across bronze ingestions.

Full refresh (TRUNCATE + INSERT). Bronze is small; incremental Silver
dedupe would need MERGE we don't need.

Dimension lookups are LEFT JOINs. Missing supplier/plant/depot rows do NOT
drop the fact — they COALESCE to 'UNKNOWN', mark dq_valid=false, and emit
a matching dq_notes tag. This preserves the "flag, don't drop" contract
even if a future source feeds bronze without honoring operational FKs.

Inventory is intentionally not promoted to Silver — it is a state snapshot,
not a fact stream. Gold reads Bronze directly if needed.
"""
from __future__ import annotations

from datetime import datetime
import logging

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook

POSTGRES_CONN_ID = "postgres_ethanova"

log = logging.getLogger(__name__)


DISPATCH_ORDERS_CLEAN_DDL = """
CREATE TABLE IF NOT EXISTS silver.dispatch_orders_clean (
    order_number          VARCHAR(50)   PRIMARY KEY,
    supplier_code         VARCHAR(50)   NOT NULL,
    plant_code            VARCHAR(50)   NOT NULL,
    depot_code            VARCHAR(50)   NOT NULL,
    ethanol_grade         VARCHAR(20)   NOT NULL,
    quantity_kl           NUMERIC(15,3) NOT NULL,
    unit_price_inr_per_l  NUMERIC(10,3) NOT NULL,
    total_amount_inr      NUMERIC(15,2) NOT NULL,
    order_date            DATE          NOT NULL,
    expected_arrival_date DATE          NOT NULL,
    actual_arrival_at     TIMESTAMPTZ,
    status                VARCHAR(30)   NOT NULL,
    source_updated_at     TIMESTAMPTZ   NOT NULL,
    dq_valid              BOOLEAN       NOT NULL,
    dq_notes              TEXT,
    _silver_built_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_silver_dispatch_order_date ON silver.dispatch_orders_clean (order_date);
CREATE INDEX IF NOT EXISTS ix_silver_dispatch_depot      ON silver.dispatch_orders_clean (depot_code);
"""

DEPOT_WEEKLY_DEMAND_DDL = """
CREATE TABLE IF NOT EXISTS silver.depot_weekly_demand (
    depot_code              VARCHAR(50)   NOT NULL,
    week_start_date         DATE          NOT NULL,
    iso_year                INT           NOT NULL,
    iso_week                INT           NOT NULL,
    petrol_dispensed_kl     NUMERIC(15,3) NOT NULL,
    target_blend_percentage NUMERIC(5,2)  NOT NULL,
    ethanol_required_kl     NUMERIC(15,3) NOT NULL,
    ethanol_blended_kl      NUMERIC(15,3) NOT NULL,
    shortfall_kl            NUMERIC(15,3) NOT NULL,
    is_supply_shortfall     BOOLEAN       NOT NULL,
    data_source             VARCHAR(30)   NOT NULL,
    source_updated_at       TIMESTAMPTZ   NOT NULL,
    dq_valid                BOOLEAN       NOT NULL,
    dq_notes                TEXT,
    _silver_built_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (depot_code, week_start_date)
);
CREATE INDEX IF NOT EXISTS ix_silver_demand_week ON silver.depot_weekly_demand (week_start_date);
"""


DISPATCH_TRANSFORM_SQL = """
TRUNCATE silver.dispatch_orders_clean;

INSERT INTO silver.dispatch_orders_clean (
    order_number, supplier_code, plant_code, depot_code,
    ethanol_grade, quantity_kl, unit_price_inr_per_l, total_amount_inr,
    order_date, expected_arrival_date, actual_arrival_at, status,
    source_updated_at, dq_valid, dq_notes
)
SELECT
    latest.order_number,
    COALESCE(s.supplier_code, 'UNKNOWN') AS supplier_code,
    COALESCE(p.plant_code,    'UNKNOWN') AS plant_code,
    COALESCE(d.depot_code,    'UNKNOWN') AS depot_code,
    latest.ethanol_grade,
    latest.quantity_kl,
    latest.unit_price_inr_per_l,
    latest.total_amount_inr,
    latest.order_date,
    latest.expected_arrival_date,
    latest.actual_arrival_at,
    latest.status,
    latest.updated_at AS source_updated_at,
    (   s.supplier_code IS NOT NULL
    AND p.plant_code    IS NOT NULL
    AND d.depot_code    IS NOT NULL
    AND latest.ethanol_grade IN ('ANHYDROUS', 'HYDROUS_95', 'DENATURED')
    AND latest.quantity_kl > 0
    AND latest.unit_price_inr_per_l > 0
    AND latest.expected_arrival_date >= latest.order_date
    AND latest.status IN ('DRAFT','CONFIRMED','DISPATCHED','IN_TRANSIT','DELIVERED','CANCELLED')
    AND ABS(latest.total_amount_inr - ROUND((latest.quantity_kl * 1000 * latest.unit_price_inr_per_l)::numeric, 2)) < 1
    ) AS dq_valid,
    NULLIF(
        CONCAT_WS('; ',
            CASE WHEN s.supplier_code IS NULL THEN 'missing_supplier' END,
            CASE WHEN p.plant_code    IS NULL THEN 'missing_plant' END,
            CASE WHEN d.depot_code    IS NULL THEN 'missing_depot' END,
            CASE WHEN latest.ethanol_grade NOT IN ('ANHYDROUS','HYDROUS_95','DENATURED')
                 THEN 'unknown_grade' END,
            CASE WHEN latest.quantity_kl <= 0 THEN 'nonpositive_quantity' END,
            CASE WHEN latest.unit_price_inr_per_l <= 0 THEN 'nonpositive_price' END,
            CASE WHEN latest.expected_arrival_date < latest.order_date
                 THEN 'expected_arrival_before_order' END,
            CASE WHEN latest.status NOT IN ('DRAFT','CONFIRMED','DISPATCHED','IN_TRANSIT','DELIVERED','CANCELLED')
                 THEN 'unknown_status' END,
            CASE WHEN ABS(latest.total_amount_inr - ROUND((latest.quantity_kl * 1000 * latest.unit_price_inr_per_l)::numeric, 2)) >= 1
                 THEN 'total_amount_mismatch' END
        ), ''
    ) AS dq_notes
FROM (
    SELECT DISTINCT ON (order_number) *
    FROM bronze.dispatch_orders_raw
    ORDER BY order_number, updated_at DESC, _ingested_at DESC
) AS latest
LEFT JOIN operational.suppliers         s ON s.id = latest.supplier_id
LEFT JOIN operational.production_plants p ON p.id = latest.source_plant_id
LEFT JOIN operational.depots            d ON d.id = latest.destination_depot_id;
"""

DEMAND_TRANSFORM_SQL = """
TRUNCATE silver.depot_weekly_demand;

INSERT INTO silver.depot_weekly_demand (
    depot_code, week_start_date, iso_year, iso_week,
    petrol_dispensed_kl, target_blend_percentage,
    ethanol_required_kl, ethanol_blended_kl,
    shortfall_kl, is_supply_shortfall,
    data_source, source_updated_at, dq_valid, dq_notes
)
SELECT
    COALESCE(d.depot_code, 'UNKNOWN') AS depot_code,
    latest.week_start_date,
    latest.iso_year,
    latest.iso_week,
    latest.petrol_dispensed_kl,
    latest.target_blend_percentage,
    latest.ethanol_required_kl,
    latest.ethanol_blended_kl,
    GREATEST(latest.ethanol_required_kl - latest.ethanol_blended_kl, 0) AS shortfall_kl,
    (latest.ethanol_blended_kl < latest.ethanol_required_kl * 0.995) AS is_supply_shortfall,
    latest.data_source,
    latest.updated_at AS source_updated_at,
    (   d.depot_code IS NOT NULL
    AND latest.petrol_dispensed_kl > 0
    AND latest.ethanol_required_kl >= 0
    AND latest.ethanol_blended_kl  >= 0
    AND latest.target_blend_percentage BETWEEN 0 AND 100
    AND latest.iso_week BETWEEN 1 AND 53
    ) AS dq_valid,
    NULLIF(
        CONCAT_WS('; ',
            CASE WHEN d.depot_code IS NULL THEN 'missing_depot' END,
            CASE WHEN latest.petrol_dispensed_kl <= 0 THEN 'nonpositive_petrol' END,
            CASE WHEN latest.ethanol_required_kl < 0 THEN 'negative_required' END,
            CASE WHEN latest.ethanol_blended_kl  < 0 THEN 'negative_blended' END,
            CASE WHEN latest.target_blend_percentage NOT BETWEEN 0 AND 100
                 THEN 'blend_pct_out_of_range' END,
            CASE WHEN latest.iso_week NOT BETWEEN 1 AND 53 THEN 'iso_week_out_of_range' END
        ), ''
    ) AS dq_notes
FROM (
    SELECT DISTINCT ON (depot_id, week_start_date) *
    FROM bronze.depot_consumption_raw
    ORDER BY depot_id, week_start_date, updated_at DESC, _ingested_at DESC
) AS latest
LEFT JOIN operational.depots d ON d.id = latest.depot_id;
"""


def transform_dispatch_orders() -> None:
    hook = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    hook.run(DISPATCH_ORDERS_CLEAN_DDL)
    hook.run(DISPATCH_TRANSFORM_SQL)
    total = hook.get_first("SELECT COUNT(*) FROM silver.dispatch_orders_clean")[0]
    invalid = hook.get_first(
        "SELECT COUNT(*) FROM silver.dispatch_orders_clean WHERE NOT dq_valid"
    )[0]
    log.info("silver.dispatch_orders_clean: %d rows total, %d flagged invalid",
             total, invalid)


def transform_demand() -> None:
    hook = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    hook.run(DEPOT_WEEKLY_DEMAND_DDL)
    hook.run(DEMAND_TRANSFORM_SQL)
    total = hook.get_first("SELECT COUNT(*) FROM silver.depot_weekly_demand")[0]
    invalid = hook.get_first(
        "SELECT COUNT(*) FROM silver.depot_weekly_demand WHERE NOT dq_valid"
    )[0]
    shortfall = hook.get_first(
        "SELECT COUNT(*) FROM silver.depot_weekly_demand WHERE is_supply_shortfall"
    )[0]
    log.info("silver.depot_weekly_demand: %d rows total, %d flagged invalid, "
             "%d flagged supply-shortfall", total, invalid, shortfall)


with DAG(
    dag_id="20_bronze_to_silver",
    description="Transform bronze raw tables into silver: dedupe, join dims, DQ flags.",
    schedule=None,
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["m7", "silver", "ethanova"],
    default_args={"owner": "data-platform", "retries": 0},
) as dag:
    dispatch = PythonOperator(
        task_id="transform_dispatch_orders",
        python_callable=transform_dispatch_orders,
    )
    demand = PythonOperator(
        task_id="transform_demand",
        python_callable=transform_demand,
    )
