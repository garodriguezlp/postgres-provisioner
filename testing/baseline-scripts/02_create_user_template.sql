-- Create user for schema
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${schema_name}_user') THEN
        CREATE USER ${schema_name}_user WITH PASSWORD '${schema_password}';
        RAISE NOTICE 'User ${schema_name}_user created';
    ELSE
        RAISE NOTICE 'User ${schema_name}_user already exists';
    END IF;
END $$;

-- Grant schema ownership and usage
GRANT ALL PRIVILEGES ON SCHEMA ${schema_name} TO ${schema_name}_user;
GRANT USAGE ON SCHEMA ${schema_name} TO ${schema_name}_user;

-- Allow user to create objects in the schema
ALTER SCHEMA ${schema_name} OWNER TO ${schema_name}_user;
