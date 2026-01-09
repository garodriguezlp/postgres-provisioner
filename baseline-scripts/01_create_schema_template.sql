-- Create all schemas
CREATE SCHEMA IF NOT EXISTS customer;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS orders;

-- Log creation
DO $$
BEGIN
    RAISE NOTICE 'Schema customer created or already exists';
    RAISE NOTICE 'Schema inventory created or already exists';
    RAISE NOTICE 'Schema orders created or already exists';
END $$;
