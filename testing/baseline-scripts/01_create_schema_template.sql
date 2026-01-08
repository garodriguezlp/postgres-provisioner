-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS ${schema_name};

-- Set default search path for the database
-- Note: This sets it for the current session
SET search_path TO ${schema_name}, public;

-- Log creation
DO $$
BEGIN
    RAISE NOTICE 'Schema ${schema_name} created or already exists';
END $$;
