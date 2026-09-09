"""
Gold transform DAG — silver -> gold star schema (lite).

Dimensions (natural keys):
  gold.dim_depot     : depot_code
  gold.dim_supplier  : supplier_code
  gold.dim_date      : date_actual

Facts:
  gold.fact_weekly_ethanol_demand    (grain: depot × week)
  gold.fact_weekly_dispatched_volume (grain: depot × supplier × grade × week)

Design choices:
- Business codes as natural keys — no surrogates (ADR #27).
- Only DQ-valid Silver rows promoted to Gold. Gold is analytics-ready.
- Full refresh (TRUNCATE + INSERT). Small volumes; incremental would add
  complexity for zero benefit.
- dim_date covers only the week range actually present in Silver; the ML
  layer or BI can extend forward if needed.
- Dims built in parallel; facts built in parallel after dims — facts
  reference dim rows implicitly by natural key (LEFT JOIN, missing dim
  rows would surface as NULLs, but dq_valid filter upstream prevents it).
"""
from __future__ import annotations

from datetime import datetime
import logging

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook

POSTGRES_CONN_ID = "postgres_ethanova"

log = logging.getLogger(__name__)


# ============================================================================
# DIMENSIONS
# ============================================================================

DIM_DEPOT_DDL = """
CREATE TABLE IF NOT EXISTS gold.dim_depot (
    depot_code           VARCHAR(50)   PRIMARY KEY,
    depot_name           VARCHAR(200)  NOT NULL,
    omc_code             VARCHAR(20)   NOT NULL,
    state_code           VARCHAR(10)   NOT NULL,
    storage_capacity_kl  NUMERIC(15,3) NOT NULL,
    reorder_threshold_kl NUMERIC(15,3),
    active               BOOLEAN       NOT NULL,
    _gold_built_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
"""

DIM_DEPOT_SQL = """
TRUNCATE gold.dim_depot;
INSERT INTO gold.dim_depot (
    depot_code, depot_name, omc_code, state_code,
    storage_capacity_kl, reorder_threshold_kl, active
)
SELECT depot_code, depot_name, omc_code, state_code,
       storage_capacity_kl, reorder_threshold_kl, active
FROM operational.depots
WHERE depot_code NOT LIKE '%TEST%';
"""

DIM_SUPPLIER_DDL = """
CREATE TABLE IF NOT EXISTS gold.dim_supplier (
    supplier_code     VARCHAR(50)  PRIMARY KEY,
    supplier_name     VARCHAR(200) NOT NULL,
    supplier_type     VARCHAR(30)  NOT NULL,
    state_code        VARCHAR(10)  NOT NULL,
    reliability_score NUMERIC(5,2),
    active            BOOLEAN      NOT NULL,
    _gold_built_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
"""

DIM_SUPPLIER_SQL = """
TRUNCATE gold.dim_supplier;
INSERT INTO gold.dim_supplier (
    supplier_code, supplier_name, supplier_type, state_code,
    reliability_score, active
)
SELECT supplier_code, supplier_name, supplier_type, state_code,
       reliability_score, active
FROM operational.suppliers
WHERE supplier_code NOT LIKE '%-999';
"""

DIM_DATE_DDL = """
CREATE TABLE IF NOT EXISTS gold.dim_date (
    date_actual     DATE        PRIMARY KEY,
    iso_year        INT         NOT NULL,
    iso_week        INT         NOT NULL,
    week_start_date DATE        NOT NULL,
    month_num       INT         NOT NULL,
    month_name      VARCHAR(20) NOT NULL,
    quarter_num     INT         NOT NULL,
    year_num        INT         NOT NULL,
    day_of_week     INT         NOT NULL,
    _gold_built_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_gold_dim_date_week_start ON gold.dim_date (week_start_date);
CREATE INDEX IF NOT EXISTS ix_gold_dim_date_iso_yw     ON gold.dim_date (iso_year, iso_week);
"""

DIM_DATE_SQL = """
TRUNCATE gold.dim_date;
INSERT INTO gold.dim_date (
    date_actual, iso_year, iso_week, week_start_date,
    month_num, month_name, quarter_num, year_num, day_of_week
)
SELECT
    d::date AS date_actual,
    EXTRACT(ISOYEAR FROM d)::int AS iso_year,
    EXTRACT(WEEK    FROM d)::int AS iso_week,
    (date_trunc('week', d))::date AS week_start_date,
    EXTRACT(MONTH   FROM d)::int AS month_num,
    TO_CHAR(d, 'FMMonth')        AS month_name,
    EXTRACT(QUARTER FROM d)::int AS quarter_num,
    EXTRACT(YEAR    FROM d)::int AS year_num,
    EXTRACT(ISODOW  FROM d)::int AS day_of_week
FROM generate_series(
    (SELECT MIN(week_start_date) FROM silver.depot_weekly_demand),
    (SELECT MAX(week_start_date) + INTERVAL '6 days' FROM silver.depot_weekly_demand),
    INTERVAL '1 day'
) AS d;
"""


# ============================================================================
# FACTS
# ============================================================================

FACT_DEMAND_DDL = """
CREATE TABLE IF NOT EXISTS gold.fact_weekly_ethanol_demand (
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
    _gold_built_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (depot_code, week_start_date)
);
CREATE INDEX IF NOT EXISTS ix_gold_fact_demand_week  ON gold.fact_weekly_ethanol_demand (week_start_date);
CREATE INDEX IF NOT EXISTS ix_gold_fact_demand_depot ON gold.fact_weekly_ethanol_demand (depot_code);
"""

FACT_DEMAND_SQL = """
TRUNCATE gold.fact_weekly_ethanol_demand;
INSERT INTO gold.fact_weekly_ethanol_demand (
    depot_code, week_start_date, iso_year, iso_week,
    petrol_dispensed_kl, target_blend_percentage,
    ethanol_required_kl, ethanol_blended_kl,
    shortfall_kl, is_supply_shortfall
)
SELECT
    depot_code, week_start_date, iso_year, iso_week,
    petrol_dispensed_kl, target_blend_percentage,
    ethanol_required_kl, ethanol_blended_kl,
    shortfall_kl, is_supply_shortfall
FROM silver.depot_weekly_demand
WHERE dq_valid;
"""

FACT_DISPATCHED_DDL = """
CREATE TABLE IF NOT EXISTS gold.fact_weekly_dispatched_volume (
    depot_code       VARCHAR(50)   NOT NULL,
    supplier_code    VARCHAR(50)   NOT NULL,
    ethanol_grade    VARCHAR(20)   NOT NULL,
    week_start_date  DATE          NOT NULL,
    iso_year         INT           NOT NULL,
    iso_week         INT           NOT NULL,
    order_count      INT           NOT NULL,
    total_volume_kl  NUMERIC(15,3) NOT NULL,
    total_value_inr  NUMERIC(18,2) NOT NULL,
    avg_price_inr_per_l NUMERIC(10,3) NOT NULL,
    _gold_built_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (depot_code, supplier_code, ethanol_grade, week_start_date)
);
CREATE INDEX IF NOT EXISTS ix_gold_fact_disp_week     ON gold.fact_weekly_dispatched_volume (week_start_date);
CREATE INDEX IF NOT EXISTS ix_gold_fact_disp_depot    ON gold.fact_weekly_dispatched_volume (depot_code);
CREATE INDEX IF NOT EXISTS ix_gold_fact_disp_supplier ON gold.fact_weekly_dispatched_volume (supplier_code);
"""

FACT_DISPATCHED_SQL = """
TRUNCATE gold.fact_weekly_dispatched_volume;
INSERT INTO gold.fact_weekly_dispatched_volume (
    depot_code, supplier_code, ethanol_grade, week_start_date,
    iso_year, iso_week, order_count, total_volume_kl,
    total_value_inr, avg_price_inr_per_l
)
SELECT
    depot_code,
    supplier_code,
    ethanol_grade,
    (date_trunc('week', order_date))::date          AS week_start_date,
    EXTRACT(ISOYEAR FROM order_date)::int           AS iso_year,
    EXTRACT(WEEK    FROM order_date)::int           AS iso_week,
    COUNT(*)::int                                   AS order_count,
    SUM(quantity_kl)                                AS total_volume_kl,
    SUM(total_amount_inr)                           AS total_value_inr,
    ROUND(
        (SUM(total_amount_inr) / NULLIF(SUM(quantity_kl) * 1000, 0))::numeric,
        3
    ) AS avg_price_inr_per_l
FROM silver.dispatch_orders_clean
WHERE dq_valid
GROUP BY depot_code, supplier_code, ethanol_grade,
         (date_trunc('week', order_date))::date,
         EXTRACT(ISOYEAR FROM order_date)::int,
         EXTRACT(WEEK    FROM order_date)::int;
"""


# ============================================================================
# TASK FUNCTIONS
# ============================================================================

def build_dim_depot() -> None:
    h = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    h.run(DIM_DEPOT_DDL)
    h.run(DIM_DEPOT_SQL)
    log.info("gold.dim_depot: %d rows",
             h.get_first("SELECT COUNT(*) FROM gold.dim_depot")[0])


def build_dim_supplier() -> None:
    h = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    h.run(DIM_SUPPLIER_DDL)
    h.run(DIM_SUPPLIER_SQL)
    log.info("gold.dim_supplier: %d rows",
             h.get_first("SELECT COUNT(*) FROM gold.dim_supplier")[0])


def build_dim_date() -> None:
    h = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    h.run(DIM_DATE_DDL)
    h.run(DIM_DATE_SQL)
    log.info("gold.dim_date: %d rows",
             h.get_first("SELECT COUNT(*) FROM gold.dim_date")[0])


def build_fact_demand() -> None:
    h = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    h.run(FACT_DEMAND_DDL)
    h.run(FACT_DEMAND_SQL)
    total = h.get_first("SELECT COUNT(*) FROM gold.fact_weekly_ethanol_demand")[0]
    shortfall = h.get_first(
        "SELECT COUNT(*) FROM gold.fact_weekly_ethanol_demand WHERE is_supply_shortfall"
    )[0]
    log.info("gold.fact_weekly_ethanol_demand: %d rows, %d shortfall weeks",
             total, shortfall)


def build_fact_dispatched() -> None:
    h = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    h.run(FACT_DISPATCHED_DDL)
    h.run(FACT_DISPATCHED_SQL)
    total = h.get_first("SELECT COUNT(*) FROM gold.fact_weekly_dispatched_volume")[0]
    log.info("gold.fact_weekly_dispatched_volume: %d rows", total)


with DAG(
    dag_id="30_silver_to_gold",
    description="Build gold star schema from silver: 3 dims + 2 facts.",
    schedule=None,
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["m8", "gold", "ethanova"],
    default_args={"owner": "data-platform", "retries": 0},
) as dag:
    d1 = PythonOperator(task_id="build_dim_depot",     python_callable=build_dim_depot)
    d2 = PythonOperator(task_id="build_dim_supplier",  python_callable=build_dim_supplier)
    d3 = PythonOperator(task_id="build_dim_date",      python_callable=build_dim_date)
    f1 = PythonOperator(task_id="build_fact_weekly_ethanol_demand",
                        python_callable=build_fact_demand)
    f2 = PythonOperator(task_id="build_fact_weekly_dispatched_volume",
                        python_callable=build_fact_dispatched)

    [d1, d2, d3] >> f1
    [d1, d2, d3] >> f2
