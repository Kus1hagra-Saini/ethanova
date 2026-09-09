# Ethanova Data Platform

Apache Airflow 2.10.5 (Python 3.11, LocalExecutor) orchestrating the Bronze → Silver → Gold pipeline that feeds the Review 2 forecast and decision-support layer.

## Layout

- `Dockerfile` — custom image extending `apache/airflow:2.10.5-python3.11`
- `requirements-airflow.txt` — additional Python dependencies (pinned via Airflow constraints)
- `dags/` — Airflow DAG definitions; bind-mounted read-only into containers
- `plugins/` — custom operators / hooks (empty at M5.2)
- `config/` — custom `airflow.cfg` overrides (empty at M5.2)
- `logs/` — Airflow task logs; bind-mounted read-write, contents gitignored

## Version pinning

Every dependency is resolved through Apache Airflow's official constraints file for release 2.10.5 on Python 3.11:
https://raw.githubusercontent.com/apache/airflow/constraints-2.10.5/constraints-3.11.txt

The Dockerfile passes this URL to `pip install --constraint`, which pins each package (and its transitive dependencies) to the exact version the Airflow team tested against this release. Consistent with ADR #8 (evidence-based version claims) and ADR #13 (Airflow in Docker only).

## Building and running

The image is built and run by `docker compose` from `deployment/docker-compose.yml` (wired in M5.3). Do not build or run this image directly during M5.2 — the scaffold is not yet connected to any orchestration.

## Related

- Airflow metadata database provisioned by `deployment/postgres/init/02-airflow-database.sql` (M5.1)
- ADR #13 (Airflow in Docker only, LocalExecutor, ~4 DAGs) — `docs/PROJECT_STATE.md` §9