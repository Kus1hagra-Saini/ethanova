-- =============================================================================
-- 02 — Airflow metadata database
-- Runs only on first container start with an empty PGDATA volume, alongside
-- 01-schemas.sql. For existing volumes (the current situation on M5.1) the
-- SQL below is also executed manually via `docker exec` — the \gexec guard
-- makes it idempotent either way.
--
-- Why a separate database and not a schema:
--   Airflow owns its own metadata tables (dag, task_instance, xcom, log,
--   variable, connection, etc.) and manages migrations via `airflow db
--   migrate`. Keeping them in a dedicated database prevents any collision
--   with the operational/bronze/silver/gold schemas and keeps the medallion
--   architecture readable.
--
-- Ownership:
--   Owned by the `ethanova` role — the role Airflow authenticates as. This
--   preserves ADR #12 (single Postgres instance) without introducing a new
--   Postgres user.
-- =============================================================================

SELECT 'CREATE DATABASE airflow OWNER ethanova ENCODING ''UTF8'''
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'airflow'
)\gexec

COMMENT ON DATABASE airflow IS
    'Apache Airflow metadata database (LocalExecutor). Managed by `airflow db migrate`.';