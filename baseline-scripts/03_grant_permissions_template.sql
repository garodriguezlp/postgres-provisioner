-- Grant default privileges for future objects in all schemas
-- This ensures that new tables, sequences, and functions are automatically accessible

-- Customer schema privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA customer
GRANT ALL ON TABLES TO customer_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA customer
GRANT ALL ON SEQUENCES TO customer_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA customer
GRANT EXECUTE ON FUNCTIONS TO customer_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA customer
GRANT USAGE ON TYPES TO customer_user;

-- Inventory schema privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA inventory
GRANT ALL ON TABLES TO inventory_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA inventory
GRANT ALL ON SEQUENCES TO inventory_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA inventory
GRANT EXECUTE ON FUNCTIONS TO inventory_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA inventory
GRANT USAGE ON TYPES TO inventory_user;

-- Orders schema privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA orders
GRANT ALL ON TABLES TO orders_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA orders
GRANT ALL ON SEQUENCES TO orders_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA orders
GRANT EXECUTE ON FUNCTIONS TO orders_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA orders
GRANT USAGE ON TYPES TO orders_user;

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'Default privileges granted for schema customer';
    RAISE NOTICE 'Default privileges granted for schema inventory';
    RAISE NOTICE 'Default privileges granted for schema orders';
END $$;
