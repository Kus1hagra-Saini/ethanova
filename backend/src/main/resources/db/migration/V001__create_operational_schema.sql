-- =============================================================================
-- V001 — Initial operational schema for Review 1
-- Six tables: users, suppliers, production_plants, depots, dispatch_orders, inventory
-- =============================================================================

-- USERS -----------------------------------------------------------------------
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(200)    NOT NULL,
    role            VARCHAR(30)     NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
);

COMMENT ON TABLE  users IS 'Application users';
COMMENT ON COLUMN users.role IS 'ADMIN | PLANNER | DEPOT_MANAGER | SUPPLIER | ANALYST';

-- SUPPLIERS -------------------------------------------------------------------
CREATE TABLE suppliers (
    id                  BIGSERIAL       PRIMARY KEY,
    supplier_code       VARCHAR(50)     NOT NULL,
    supplier_name       VARCHAR(200)    NOT NULL,
    supplier_type       VARCHAR(30)     NOT NULL,
    state_code          VARCHAR(10)     NOT NULL,
    contact_email       VARCHAR(255),
    contact_phone       VARCHAR(20),
    reliability_score   NUMERIC(5,2),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_suppliers_code UNIQUE (supplier_code)
);

CREATE INDEX ix_suppliers_state  ON suppliers (state_code);
CREATE INDEX ix_suppliers_type   ON suppliers (supplier_type);
CREATE INDEX ix_suppliers_active ON suppliers (active);

COMMENT ON TABLE  suppliers IS 'Ethanol suppliers — sugar mills, grain distilleries';
COMMENT ON COLUMN suppliers.supplier_type IS 'SUGAR_MILL | GRAIN_DISTILLERY | DUAL_FEED';
COMMENT ON COLUMN suppliers.reliability_score IS 'Computed 0.00–100.00; populated by data platform';

-- PRODUCTION PLANTS -----------------------------------------------------------
CREATE TABLE production_plants (
    id                      BIGSERIAL       PRIMARY KEY,
    plant_code              VARCHAR(50)     NOT NULL,
    plant_name              VARCHAR(200)    NOT NULL,
    supplier_id             BIGINT          NOT NULL,
    capacity_kl_per_day     NUMERIC(15,3)   NOT NULL,
    state_code              VARCHAR(10)     NOT NULL,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_production_plants_code    UNIQUE (plant_code),
    CONSTRAINT fk_production_plants_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE RESTRICT
);

CREATE INDEX ix_production_plants_supplier ON production_plants (supplier_id);
CREATE INDEX ix_production_plants_state    ON production_plants (state_code);

COMMENT ON TABLE production_plants IS 'Production facilities operated by suppliers';

-- DEPOTS ----------------------------------------------------------------------
CREATE TABLE depots (
    id                      BIGSERIAL       PRIMARY KEY,
    depot_code              VARCHAR(50)     NOT NULL,
    depot_name              VARCHAR(200)    NOT NULL,
    omc_code                VARCHAR(20)     NOT NULL,
    state_code              VARCHAR(10)     NOT NULL,
    storage_capacity_kl     NUMERIC(15,3)   NOT NULL,
    reorder_threshold_kl    NUMERIC(15,3),
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_depots_code UNIQUE (depot_code)
);

CREATE INDEX ix_depots_omc   ON depots (omc_code);
CREATE INDEX ix_depots_state ON depots (state_code);

COMMENT ON TABLE  depots IS 'OMC storage and blending depots';
COMMENT ON COLUMN depots.omc_code IS 'IOCL | BPCL | HPCL | NAYARA | RELIANCE';

-- DISPATCH ORDERS -------------------------------------------------------------
CREATE TABLE dispatch_orders (
    id                          BIGSERIAL       PRIMARY KEY,
    order_number                VARCHAR(50)     NOT NULL,
    supplier_id                 BIGINT          NOT NULL,
    source_plant_id             BIGINT          NOT NULL,
    destination_depot_id        BIGINT          NOT NULL,
    ethanol_grade               VARCHAR(20)     NOT NULL,
    quantity_kl                 NUMERIC(15,3)   NOT NULL,
    unit_price_inr_per_l        NUMERIC(10,3)   NOT NULL,
    total_amount_inr            NUMERIC(15,2)   NOT NULL,
    order_date                  DATE            NOT NULL,
    expected_arrival_date       DATE            NOT NULL,
    actual_arrival_at           TIMESTAMPTZ,
    status                      VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                     BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_dispatch_orders_number   UNIQUE (order_number),
    CONSTRAINT fk_dispatch_orders_supplier FOREIGN KEY (supplier_id)          REFERENCES suppliers          (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dispatch_orders_plant    FOREIGN KEY (source_plant_id)      REFERENCES production_plants  (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dispatch_orders_depot    FOREIGN KEY (destination_depot_id) REFERENCES depots             (id) ON DELETE RESTRICT
);

CREATE INDEX ix_dispatch_orders_supplier ON dispatch_orders (supplier_id);
CREATE INDEX ix_dispatch_orders_depot    ON dispatch_orders (destination_depot_id);
CREATE INDEX ix_dispatch_orders_status   ON dispatch_orders (status);
CREATE INDEX ix_dispatch_orders_arrival  ON dispatch_orders (expected_arrival_date);

COMMENT ON TABLE  dispatch_orders IS 'Procurement dispatch orders from supplier to depot';
COMMENT ON COLUMN dispatch_orders.status IS 'DRAFT | CONFIRMED | DISPATCHED | IN_TRANSIT | DELIVERED | CANCELLED';
COMMENT ON COLUMN dispatch_orders.ethanol_grade IS 'ANHYDROUS | HYDROUS_95 | DENATURED';

-- INVENTORY -------------------------------------------------------------------
CREATE TABLE inventory (
    id                      BIGSERIAL       PRIMARY KEY,
    depot_id                BIGINT          NOT NULL,
    ethanol_grade           VARCHAR(20)     NOT NULL,
    current_stock_kl        NUMERIC(15,3)   NOT NULL DEFAULT 0,
    max_capacity_kl         NUMERIC(15,3)   NOT NULL,
    reorder_level_kl        NUMERIC(15,3),
    last_updated_at         TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_depot_grade UNIQUE (depot_id, ethanol_grade),
    CONSTRAINT fk_inventory_depot       FOREIGN KEY (depot_id) REFERENCES depots (id) ON DELETE RESTRICT
);

CREATE INDEX ix_inventory_depot ON inventory (depot_id);

COMMENT ON TABLE inventory IS 'Current inventory per depot × ethanol grade';