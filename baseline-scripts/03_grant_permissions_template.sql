-- Grant default privileges for future objects in the schema
-- This ensures that new tables, sequences, and functions are automatically accessible

-- For tables
ALTER DEFAULT PRIVILEGES IN SCHEMA ${schema_name}
GRANT ALL ON TABLES TO ${schema_name}_user;

-- For sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA ${schema_name}
GRANT ALL ON SEQUENCES TO ${schema_name}_user;

-- For functions
ALTER DEFAULT PRIVILEGES IN SCHEMA ${schema_name}
GRANT EXECUTE ON FUNCTIONS TO ${schema_name}_user;

-- For types
ALTER DEFAULT PRIVILEGES IN SCHEMA ${schema_name}
GRANT USAGE ON TYPES TO ${schema_name}_user;

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'Default privileges granted for schema ${schema_name}';
END $$;
