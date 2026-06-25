-- TradePulse — PostgreSQL initialisation script
-- Runs once on first container boot via docker-entrypoint-initdb.d
-- The 'tradepulse_auth' database is created by POSTGRES_DB env var.
-- This script creates all remaining service databases and grants privileges.

\echo 'Creating TradePulse databases...'

-- auth-service (default DB already created via POSTGRES_DB env var)
-- tradepulse_auth already exists

CREATE DATABASE tradepulse_users;
CREATE DATABASE tradepulse_orders;
CREATE DATABASE tradepulse_portfolio;
CREATE DATABASE tradepulse_reporting;

\echo 'Granting privileges...'

GRANT ALL PRIVILEGES ON DATABASE tradepulse_auth      TO tradepulse;
GRANT ALL PRIVILEGES ON DATABASE tradepulse_users     TO tradepulse;
GRANT ALL PRIVILEGES ON DATABASE tradepulse_orders    TO tradepulse;
GRANT ALL PRIVILEGES ON DATABASE tradepulse_portfolio TO tradepulse;
GRANT ALL PRIVILEGES ON DATABASE tradepulse_reporting TO tradepulse;

\echo 'Databases created:'
\list
