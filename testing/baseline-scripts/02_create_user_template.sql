-- Create users for all schemas
DO $$
BEGIN
    -- Customer user
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'customer_user') THEN
        CREATE USER customer_user WITH PASSWORD 'customer_pass';
        RAISE NOTICE 'User customer_user created';
    ELSE
        RAISE NOTICE 'User customer_user already exists';
    END IF;
    
    -- Inventory user
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'inventory_user') THEN
        CREATE USER inventory_user WITH PASSWORD 'inventory_pass';
        RAISE NOTICE 'User inventory_user created';
    ELSE
        RAISE NOTICE 'User inventory_user already exists';
    END IF;
    
    -- Orders user
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'orders_user') THEN
        CREATE USER orders_user WITH PASSWORD 'orders_pass';
        RAISE NOTICE 'User orders_user created';
    ELSE
        RAISE NOTICE 'User orders_user already exists';
    END IF;
END $$;

-- Grant schema ownership and usage for customer
GRANT ALL PRIVILEGES ON SCHEMA customer TO customer_user;
GRANT USAGE ON SCHEMA customer TO customer_user;
ALTER SCHEMA customer OWNER TO customer_user;

-- Grant schema ownership and usage for inventory
GRANT ALL PRIVILEGES ON SCHEMA inventory TO inventory_user;
GRANT USAGE ON SCHEMA inventory TO inventory_user;
ALTER SCHEMA inventory OWNER TO inventory_user;

-- Grant schema ownership and usage for orders
GRANT ALL PRIVILEGES ON SCHEMA orders TO orders_user;
GRANT USAGE ON SCHEMA orders TO orders_user;
ALTER SCHEMA orders OWNER TO orders_user;
