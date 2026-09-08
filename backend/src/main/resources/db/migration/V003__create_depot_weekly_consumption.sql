-- =============================================================================
-- V003 — Weekly petrol dispensing and ethanol blending per depot
-- Purpose: capture the demand signal used by the Silver/Gold forecast pipeline.
--          One row per (depot, ISO week). Populated by the simulator for
--          historical backfill; may later be fed by a real OMC data source.
-- Additive migration — no changes to existing tables.
-- =============================================================================

CREATE TABLE depot_weekly_consumption (
    id                          BIGSERIAL       PRIMARY KEY,
    depot_id                    BIGINT          NOT NULL,
    week_start_date             DATE            NOT NULL,
    iso_year                    INT             NOT NULL,
    iso_week                    INT             NOT NULL,
    petrol_dispensed_kl         NUMERIC(15,3)   NOT NULL,
    target_blend_percentage     NUMERIC(5,2)    NOT NULL,
    ethanol_required_kl         NUMERIC(15,3)   NOT NULL,
    ethanol_blended_kl          NUMERIC(15,3)   NOT NULL,
    data_source                 VARCHAR(30)     NOT NULL DEFAULT 'SIMULATOR',
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                     BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_depot_weekly_consumption_depot_week
        UNIQUE (depot_id, week_start_date),
    CONSTRAINT fk_depot_weekly_consumption_depot
        FOREIGN KEY (depot_id) REFERENCES depots (id) ON DELETE RESTRICT,
    CONSTRAINT ck_depot_weekly_consumption_iso_week
        CHECK (iso_week BETWEEN 1 AND 53),
    CONSTRAINT ck_depot_weekly_consumption_blend_pct
        CHECK (target_blend_percentage >= 0 AND target_blend_percentage <= 100)
);

CREATE INDEX ix_depot_weekly_consumption_depot     ON depot_weekly_consumption (depot_id);
CREATE INDEX ix_depot_weekly_consumption_week      ON depot_weekly_consumption (week_start_date);
CREATE INDEX ix_depot_weekly_consumption_year_week ON depot_weekly_consumption (iso_year, iso_week);

COMMENT ON TABLE  depot_weekly_consumption IS
    'Weekly petrol dispensing and ethanol blending per depot - demand signal for the forecast pipeline';
COMMENT ON COLUMN depot_weekly_consumption.week_start_date IS
    'Monday of the ISO week (ISO 8601)';
COMMENT ON COLUMN depot_weekly_consumption.petrol_dispensed_kl IS
    'Total petrol dispensed at the depot for the week - primary demand driver';
COMMENT ON COLUMN depot_weekly_consumption.target_blend_percentage IS
    'Policy target blend percentage in effect for the week (e.g. 10.00 for E10, 20.00 for E20)';
COMMENT ON COLUMN depot_weekly_consumption.ethanol_required_kl IS
    'Ethanol required to meet target blend = petrol_dispensed_kl * target_blend_percentage / 100. Forecast target';
COMMENT ON COLUMN depot_weekly_consumption.ethanol_blended_kl IS
    'Ethanol actually blended for the week - may fall short of required if supply-constrained';
COMMENT ON COLUMN depot_weekly_consumption.data_source IS
    'SIMULATOR | REAL - provenance flag for downstream filtering';