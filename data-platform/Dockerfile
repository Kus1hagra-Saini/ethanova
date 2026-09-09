# syntax=docker/dockerfile:1
# =============================================================================
# Ethanova Airflow image — extends the official Apache Airflow image
# Base:  apache/airflow:2.10.5-python3.11
# Extras added:
#   - apache-airflow-providers-postgres  (required by PostgresHook)
#   - psycopg2-binary                    (explicit; also a provider transitive)
#   - pandas / numpy                     (Bronze/Silver/Gold DataFrame work)
# SQLAlchemy is already installed by Airflow itself at a version pinned by the
# constraints file — do not add it here.
#
# Reproducible install:
#   Every package (and every transitive dependency) is pinned via Apache
#   Airflow's official constraints file for release 2.10.5 on Python 3.11.
#   The file is a frozen artefact at the constraints-2.10.5 tag — it does not
#   change after the release. This is the Airflow project's supported way to
#   guarantee a reproducible install.
# =============================================================================
ARG AIRFLOW_VERSION=2.10.5
ARG PYTHON_VERSION=3.11
FROM apache/airflow:${AIRFLOW_VERSION}-python${PYTHON_VERSION}

# Re-declare ARGs after FROM so they remain visible in this build stage
ARG AIRFLOW_VERSION
ARG PYTHON_VERSION

# Base image already sets USER airflow, but state it explicitly for clarity
USER airflow

COPY requirements-airflow.txt /requirements-airflow.txt

RUN pip install --no-cache-dir -r /requirements-airflow.txt \
    --constraint "https://raw.githubusercontent.com/apache/airflow/constraints-${AIRFLOW_VERSION}/constraints-${PYTHON_VERSION}.txt"