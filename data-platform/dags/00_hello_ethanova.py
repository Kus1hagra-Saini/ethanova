"""
Smoke DAG — Ethanova Airflow ↔ operational database connectivity check.

Runs one task that queries operational.depot_weekly_consumption via
PostgresHook and logs the row count. Proves the whole chain works end-to-end:
scheduler → PostgresHook → postgres_ethanova connection → ethanova database
→ M4 simulator data.

Manual trigger only; not a real pipeline task. Serves as the template for
Bronze/Silver/Gold DAGs.
"""
from __future__ import annotations

from datetime import datetime
import logging

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook

POSTGRES_CONN_ID = "postgres_ethanova"
SMOKE_QUERY = "SELECT COUNT(*) FROM operational.depot_weekly_consumption"

log = logging.getLogger(__name__)


def count_consumption_rows() -> int:
    hook = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    row = hook.get_first(SMOKE_QUERY)
    count = int(row[0]) if row else 0
    log.info("Consumption row count: %s", count)
    if count <= 0:
        raise ValueError(
            f"Expected > 0 rows in operational.depot_weekly_consumption, got {count}. "
            "Check that the M4 simulator has been run against this database."
        )
    return count


with DAG(
    dag_id="00_hello_ethanova",
    description="Smoke DAG: verify Airflow can query the operational database.",
    schedule=None,
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["m5", "smoke", "ethanova"],
    default_args={"owner": "data-platform", "retries": 0},
) as dag:
    PythonOperator(
        task_id="count_consumption_rows",
        python_callable=count_consumption_rows,
    )
