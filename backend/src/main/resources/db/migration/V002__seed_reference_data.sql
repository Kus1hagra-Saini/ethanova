-- =============================================================================
-- V002 — Seed reference data for Review 1 demo
-- BCrypt hash below is for password 'admin' — DEVELOPMENT ONLY
-- =============================================================================

-- USERS -----------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, full_name, role) VALUES
    ('admin',    'admin@ethanova.local',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System Administrator',   'ADMIN'),
    ('planner',  'planner@ethanova.local',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Regional Planner',       'PLANNER'),
    ('depot_mgr','depot_mgr@ethanova.local','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Depot Manager (Kanpur)', 'DEPOT_MANAGER');

-- SUPPLIERS -------------------------------------------------------------------
INSERT INTO suppliers (supplier_code, supplier_name, supplier_type, state_code, contact_email, reliability_score) VALUES
    ('SUP-UP-001', 'Bajaj Hindusthan Sugar Ltd',      'SUGAR_MILL',       'UP', 'ops@bajajhindusthan.example',      92.50),
    ('SUP-UP-002', 'Dhampur Sugar Mills',             'SUGAR_MILL',       'UP', 'ethanol@dhampursugar.example',     88.75),
    ('SUP-UP-003', 'Balrampur Chini Mills',           'SUGAR_MILL',       'UP', 'sales@balrampurchini.example',     94.20),
    ('SUP-UP-004', 'Triveni Engineering & Industries','DUAL_FEED',        'UP', 'ethanol@trivenigroup.example',     85.10),
    ('SUP-UP-005', 'India Glycols Ltd',               'GRAIN_DISTILLERY', 'UP', 'procurement@indiaglycols.example', 89.60);

-- PRODUCTION PLANTS -----------------------------------------------------------
INSERT INTO production_plants (plant_code, plant_name, supplier_id, capacity_kl_per_day, state_code)
SELECT 'PLT-UP-001', 'Bajaj Palia Plant',      id, 320.000, 'UP' FROM suppliers WHERE supplier_code = 'SUP-UP-001'
UNION ALL SELECT 'PLT-UP-002', 'Bajaj Barkhera Plant',   id, 280.000, 'UP' FROM suppliers WHERE supplier_code = 'SUP-UP-001'
UNION ALL SELECT 'PLT-UP-003', 'Dhampur Asmoli Plant',   id, 240.000, 'UP' FROM suppliers WHERE supplier_code = 'SUP-UP-002'
UNION ALL SELECT 'PLT-UP-004', 'Balrampur Haidergarh',   id, 300.000, 'UP' FROM suppliers WHERE supplier_code = 'SUP-UP-003'
UNION ALL SELECT 'PLT-UP-005', 'Triveni Muzaffarnagar',  id, 200.000, 'UP' FROM suppliers WHERE supplier_code = 'SUP-UP-004'
UNION ALL SELECT 'PLT-UP-006', 'India Glycols Kashipur', id, 180.000, 'UP' FROM suppliers WHERE supplier_code = 'SUP-UP-005';

-- DEPOTS ----------------------------------------------------------------------
INSERT INTO depots (depot_code, depot_name, omc_code, state_code, storage_capacity_kl, reorder_threshold_kl) VALUES
    ('DEP-UP-KNP', 'IOCL Kanpur Depot',    'IOCL', 'UP', 5000.000, 1000.000),
    ('DEP-UP-LKO', 'BPCL Lucknow Depot',   'BPCL', 'UP', 4500.000,  900.000),
    ('DEP-UP-VNS', 'HPCL Varanasi Depot',  'HPCL', 'UP', 3800.000,  760.000),
    ('DEP-UP-AGR', 'IOCL Agra Depot',      'IOCL', 'UP', 4200.000,  840.000),
    ('DEP-UP-MDB', 'BPCL Moradabad Depot', 'BPCL', 'UP', 3500.000,  700.000);

-- INVENTORY (one row per depot × ANHYDROUS grade) -----------------------------
INSERT INTO inventory (depot_id, ethanol_grade, current_stock_kl, max_capacity_kl, reorder_level_kl)
SELECT id, 'ANHYDROUS', ROUND((RANDOM() * (max_capacity_kl - reorder_threshold_kl) + reorder_threshold_kl)::numeric, 3),
       storage_capacity_kl, reorder_threshold_kl
FROM depots
CROSS JOIN LATERAL (SELECT storage_capacity_kl AS max_capacity_kl) mc;