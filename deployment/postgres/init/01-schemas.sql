-- Ethanova PostgreSQL initial schema layout.
-- Runs once when the Postgres container is initialised for the first time.
-- To re-run: `docker compose down -v` then `docker compose up`.

CREATE SCHEMA IF NOT EXISTS operational;
CREATE SCHEMA IF NOT EXISTS bronze;
CREATE SCHEMA IF NOT EXISTS silver;
CREATE SCHEMA IF NOT EXISTS gold;

COMMENT ON SCHEMA operational IS 'Spring Boot OLTP tables — source of truth for business events';
COMMENT ON SCHEMA bronze       IS 'Raw ingested events, immutable, minimally transformed';
COMMENT ON SCHEMA silver       IS 'Cleansed, validated, deduplicated, contract-conforming';
COMMENT ON SCHEMA gold         IS 'Business-ready star schema — facts and dimensions';

-- Set search_path for the ethanova user to prefer operational schema by default.
ALTER ROLE ethanova SET search_path TO operational, public;