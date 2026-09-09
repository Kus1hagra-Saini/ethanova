"""
Bronze extract DAG — operational -> bronze schema.

Pulls three tables from operational into bronze with an updated_at watermark.
Bronze is append-only: same source row appearing again with a newer
updated_at simply lands as a new bronze row. Silver deduplicates.

Watermark strategy: read MAX(updated_at) from the target bronze table itself.
No Airflow Variables, no XCom — the data carries its own state. First run
sees an empty bronze table and pulls everything.

Tables produced:
  bronze.dispatch_orders_raw
  bronze.inventory_snapshot_raw
  bronze.depot_consumption_raw

All three tasks run in parallel — no dependencies between the three sources.
"""
from __future__ import annotations

from datetime import datetime
import logging

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook

POSTGRES_CONN_ID = "postgres_ethanova"

log = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Source -> Bronze table specs
# ---------------------------------------------------------------------------
# Each spec: source columns (types preserved), target bronze table, DDL.
# Bronze columns mirror source column names 1:1, plus _ingested_at.
# ---------------------------------------------------------------------------

DISPATCH_ORDERS_DDL = """
CREATE TABLE IF NOT EXISTS bronze.dispatch_orders_raw (
    id                    BIGINT,
    order_number          VARCHAR(50),
    supplier_id           BIGINT,
    source_plant_id       BIGINT,
    destination_depot_id  BIGINT,
    ethanol_grade         VARCHAR(20),
    quantity_kl           NUMERIC(15,3),
    unit_price_inr_per_l  NUMERIC(10,3),
    total_amount_inr      NUMERIC(15,2),
    order_date            DATE,
    expected_arrival_date DATE,
    actual_arrival_at     TIMESTAMPTZ,
    status                VARCHAR(30),
    created_at            TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ,
    version               BIGINT,
    _ingested_at          TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_bronze_dispatch_orders_updated_at
    ON bronze.dispatch_orders_raw (updated_at);
"""

INVENTORY_DDL = """
CREATE TABLE IF NOT EXISTS bronze.inventory_snapshot_raw (
    id                BIGINT,
    depot_id          BIGINT,
    ethanol_grade     VARCHAR(20),
    current_stock_kl  NUMERIC(15,3),
    max_capacity_kl   NUMERIC(15,3),
    reorder_level_kl  NUMERIC(15,3),
    last_updated_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    version           BIGINT,
    _ingested_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_bronze_inventory_updated_at
    ON bronze.inventory_snapshot_raw (updated_at);
"""

CONSUMPTION_DDL = """
CREATE TABLE IF NOT EXISTS bronze.depot_consumption_raw (
    id                      BIGINT,
    depot_id                BIGINT,
    week_start_date         DATE,
    iso_year                INT,
    iso_week                INT,
    petrol_dispensed_kl     NUMERIC(15,3),
    target_blend_percentage NUMERIC(5,2),
    ethanol_required_kl     NUMERIC(15,3),
    ethanol_blended_kl      NUMERIC(15,3),
    data_source             VARCHAR(30),
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,
    version                 BIGINT,
    _ingested_at            TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_bronze_consumption_updated_at
    ON bronze.depot_consumption_raw (updated_at);
"""


SPECS = [
    {
        "name": "dispatch_orders",
        "source_table": "operational.dispatch_orders",
        "bronze_table": "bronze.dispatch_orders_raw",
        "ddl": DISPATCH_ORDERS_DDL,
        "columns": [
            "id", "order_number", "supplier_id", "source_plant_id",
            "destination_depot_id", "ethanol_grade", "quantity_kl",
            "unit_price_inr_per_l", "total_amount_inr", "order_date",
            "expected_arrival_date", "actual_arrival_at", "status",
            "created_at", "updated_at", "version",
        ],
    },
    {
        "name": "inventory",
        "source_table": "operational.inventory",
        "bronze_table": "bronze.inventory_snapshot_raw",
        "ddl": INVENTORY_DDL,
        "columns": [
            "id", "depot_id", "ethanol_grade", "current_stock_kl",
            "max_capacity_kl", "reorder_level_kl", "last_updated_at",
            "created_at", "updated_at", "version",
        ],
    },
    {
        "name": "consumption",
        "source_table": "operational.depot_weekly_consumption",
        "bronze_table": "bronze.depot_consumption_raw",
        "ddl": CONSUMPTION_DDL,
        "columns": [
            "id", "depot_id", "week_start_date", "iso_year", "iso_week",
            "petrol_dispensed_kl", "target_blend_percentage",
            "ethanol_required_kl", "ethanol_blended_kl", "data_source",
            "created_at", "updated_at", "version",
        ],
    },
]


def extract_to_bronze(spec: dict) -> None:
    """Extract source_table rows with updated_at > watermark into bronze_table."""
    hook = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    src = spec["source_table"]
    dst = spec["bronze_table"]
    cols = spec["columns"]

    # 1. Ensure bronze table exists
    hook.run(spec["ddl"])

    # 2. Read watermark from bronze itself
    row = hook.get_first(f"SELECT COALESCE(MAX(updated_at), 'epoch'::timestamptz) FROM {dst}")
    watermark = row[0]
    log.info("[%s] Watermark: %s", spec["name"], watermark)

    # 3. Read new/updated rows from source
    col_list = ", ".join(cols)
    select_sql = f"SELECT {col_list} FROM {src} WHERE updated_at > %s ORDER BY updated_at"
    records = hook.get_records(select_sql, parameters=(watermark,))
    log.info("[%s] Rows fetched from %s: %d", spec["name"], src, len(records))

    if not records:
        log.info("[%s] Nothing to ingest.", spec["name"])
        return

    # 4. Bulk insert into bronze (excluding _ingested_at — uses DEFAULT NOW())
    placeholders = ", ".join(["%s"] * len(cols))
    insert_sql = f"INSERT INTO {dst} ({col_list}) VALUES ({placeholders})"
    hook.insert_rows(
        table=dst,
        rows=records,
        target_fields=cols,
        commit_every=1000,
    )
    log.info("[%s] Inserted %d rows into %s.", spec["name"], len(records), dst)


with DAG(
    dag_id="10_extract_operational_to_bronze",
    description="Extract operational tables to bronze with an updated_at watermark.",
    schedule=None,
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["m6", "bronze", "ethanova"],
    default_args={"owner": "data-platform", "retries": 0},
) as dag:
    for spec in SPECS:
        PythonOperator(
            task_id=f"extract_{spec['name']}",
            python_callable=extract_to_bronze,
            op_kwargs={"spec": spec},
        )
