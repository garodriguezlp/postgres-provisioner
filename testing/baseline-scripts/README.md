# Baseline SQL Scripts

These scripts create the foundational database structure before Flyway migrations are applied.

## Usage

These scripts are executed by the FlywayProvisioner in the order:
1. `01_create_schema_template.sql` - Creates the schema
2. `02_create_user_template.sql` - Creates the schema user
3. `03_grant_permissions_template.sql` - Sets up default privileges

## Variable Substitution

The scripts use placeholders that are replaced at runtime:
- `${schema_name}` - Name of the schema being provisioned
- `${schema_password}` - Password for the schema user (auto-generated or provided)
- `${database_name}` - Name of the target database
