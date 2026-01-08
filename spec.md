# PostgreSQL Provisioner - Technical Specification

## Overview

A suite of Java 17 JBang-powered command-line tools for PostgreSQL database provisioning and migration management. The
project consists of two independent JBang scripts that automate database configuration and schema migrations via SSH and
Flyway.

---

## Project Requirements

### Technology Stack

- **Build Tool**: JBang (single-file executable scripts with inline dependencies)
- **Language**: Java 17 (leverage modern features like records, text blocks, switch expressions)
- **CLI Framework**: Picocli
    - Use `@Mixin` for reusable parameter groups between commands
    - Enable mixin help visibility
- **Migration Tool**: Flyway Core
- **HTTP Client**: OkHttp (for downloading migration artifacts from URLs)
- **Logging Framework**: Tinylog 2.x (https://tinylog.org/)
    - Include SLF4J-to-Tinylog bridge for Flyway logging integration
- **SSH Client**: JSch or Apache SSHD
- **Compression**: Built-in Java NIO or Apache Commons Compress for ZIP extraction

### Code Quality Standards

#### Architecture

- **Single-File Design**: Each script is a standalone JBang file with all logic self-contained
- **Static Nested Classes**: Organize functionality into logical groupings (e.g., `ArtifactoryClient`,
  `DatabaseManager`, `SshExecutor`)
- **Separation of Concerns**: Clear boundaries between HTTP operations, database operations, SSH operations, and
  reporting

#### Method Design

- **Small, Focused Methods**: Each method should represent a single level of abstraction
- **Clear Naming**: Use descriptive method names that indicate intent
- **Minimal Parameters**: Prefer passing context objects over many individual parameters

#### Code Style

- **Self-Documenting**: Use meaningful variable and method names
- **Inline Comments**: Document complex logic, business rules, and non-obvious decisions
- **Java 17 Features**: Use records for DTOs, text blocks for SQL/multi-line strings

#### Logging Configuration

- **Framework**: Tinylog 2.x configured programmatically (static initializer or Java code)
- **Log Pattern**: `%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] %logger{36} - %msg%n`
- **Level Control**:
    - Default: INFO level
    - With `--verbose`: DEBUG level (configured via static initializer based on CLI parameter)
- **SLF4J Bridge**: Include `tinylog-slf4j` to capture Flyway's SLF4J logs

#### Configuration Management

- **CLI Arguments**: All settings configurable via command-line arguments
- **Default Provider**: Use Picocli's `PropertiesDefaultProvider` for default values
- **Properties Format**:
    - Configuration file: `application.properties`
    - Description keys: `my.property.description=Description text`
    - Value keys: `my.property=default-value`
- **Environment Variables**: Support `${ENV_VAR_NAME}` interpolation for sensitive values
- **Override Order**: CLI args > Environment vars > Properties file > Hardcoded defaults

#### Verbosity Control

- **Flag**: `--verbose` or `-v`
- **Behavior**:
    - Standard mode: INFO-level logs, progress indicators, summary reports
    - Verbose mode: DEBUG-level logs, detailed SQL statements, HTTP request/response details, Flyway migration file
      names

---

## Script 1: PostgreSQL SSH Configurator

**File**: `PostgresSshConfigurator.java`

### Purpose

Connects to a remote PostgreSQL server via SSH to configure it for remote connections and restart the service. Keeps
things simple with basic IP, user, and password authentication.

### Responsibilities

#### 1. SSH Connection Management

- Establish secure SSH connection to target server
- Support password-based authentication
- Manage connection lifecycle (connect, execute commands, disconnect)
- Handle connection timeouts and retries

#### 2. PostgreSQL Configuration

- Modify `postgresql.conf` to enable remote connections:
    - Set `listen_addresses = '*'` (or specific IP)
    - Adjust `max_connections` if needed
- Update `pg_hba.conf` with appropriate access rules:
    - Add host-based authentication entries
    - Configure authentication method (md5, scram-sha-256)
- Backup original configuration files before modification
- Validate syntax of modified configuration files

#### 3. Service Management

- Restart PostgreSQL service to apply changes
- Verify service status after restart
- Handle restart failures gracefully with rollback option
- Wait for PostgreSQL to be ready to accept connections

### Command-Line Interface

```bash
jbang PostgresSshConfigurator.java \
  --host <hostname-or-ip> \
  --port <ssh-port> \             # Default: 22
  --user <ssh-username> \
  --password <ssh-password> \
  [--pg-config-path <path>] \     # Default: /etc/postgresql/15/main/
  [--pg-version <version>] \      # Default: 15
  [--verbose]
```

#### Parameters

- `--host` (required): Target server hostname or IP address
- `--port`: SSH port (default: 22)
- `--user` (required): SSH username
- `--password` (required): SSH password
- `--pg-config-path`: PostgreSQL configuration directory path
- `--pg-version`: PostgreSQL version for path detection
- `--verbose` / `-v`: Enable debug logging

### Execution Flow

1. **Validate Parameters**: Check required parameters and connectivity prerequisites
2. **SSH Connect**: Establish SSH connection with retry logic
3. **Locate Config Files**: Find `postgresql.conf` and `pg_hba.conf`
4. **Backup Configs**: Create timestamped backup copies
5. **Modify Configs**: Apply necessary changes for remote access
6. **Restart Service**: Execute `systemctl restart postgresql` (or equivalent)
7. **Verify Status**: Check service status and connection readiness
8. **Report Results**: Display success/failure with detailed logging

### Expected Behavior

- Successfully connect to remote server via SSH
- Create backups of existing configuration files (with timestamps)
- Apply necessary configuration changes for remote access
- Restart PostgreSQL service safely
- Verify PostgreSQL is running and accepting connections
- Report success/failure with detailed logging and actionable error messages

---

## Script 2: Flyway Migration Orchestrator

**File**: `FlywayProvisioner.java`

### Purpose

Automates database schema provisioning by downloading migration artifacts from URLs (simulating Artifactory), extracting
them, and executing Flyway migrations with baseline initialization. The database will contain multiple schemas, each
with its own versioned migrations.

### Input Format

Schema-to-version mappings as comma-separated pairs:

```
--schemas schema1:version1,schema2:version2,schema3:version3
```

Example:

```
--schemas customer:1.5.0,orders:2.3.1,inventory:1.0.0
```

### URL Construction Rules

Based on the schema name and version, construct download URLs using a template pattern:

**Template**:

```
{artifactory-base-url}/{schema-name}/{schema-name}-{version}.zip
```

**Example**:

- Base URL: `https://artifactory.company.com/migrations`
- Schema: `customer:1.5.0`
- Result: `https://artifactory.company.com/migrations/customer/customer-1.5.0.zip`

### Responsibilities

#### 1. Artifact Management

- **Download**: Pull migration ZIP files from constructed URLs using OkHttp
    - Support multiple schemas and versions in a single execution
    - Parse schema:version mappings from CLI input
    - Construct download URLs based on template pattern
    - Handle HTTP authentication if credentials provided
    - Track download progress and report failures
- **Extraction**: Unzip downloaded artifacts to temporary working directory
    - Preserve directory structure from ZIP
    - Validate ZIP integrity before extraction
- **Validation**: Verify artifact structure matches expected Flyway format
    - Check for V*.sql (versioned migrations) and R*.sql (repeatable migrations)

#### 2. Baseline Initialization

Baseline scripts are **static assets** bundled with the project (not downloaded). These scripts create the foundational
database structure.

- **Schema Creation**: Execute baseline SQL scripts to create schemas
    - Create schema if not exists
    - Set default search path for schema
- **User Management**: Create database users with appropriate permissions
    - Create role/user for each schema
    - Grant schema ownership and default privileges
    - Configure permissions so future objects are automatically accessible
- **Initial Setup**: Create prerequisite database objects
    - Extensions (e.g., uuid-ossp, pgcrypto)
    - Tablespaces (if needed)
    - Base grants and permissions

**Baseline Scripts Location**: `./baseline-scripts/` (relative to script or configurable)

#### 3. Flyway Migration Execution

- **Migrate**: Run Flyway migrations (DO operations only, no UNDO support)
    - Configure Flyway programmatically for each schema
    - Set schema-specific locations for migration files
    - Execute baseline first, then migrate
- **Versioning**: Track and report migration versions applied
    - Record in Flyway's schema_history table
    - Report current version after migration
- **Error Handling**:
    - Graceful handling of migration failures
    - Continue with other schemas if one fails (configurable)
    - Detailed error reporting with SQL file names and line numbers

#### 4. Reporting

- **Progress Tracking**: Real-time progress indicators
    - Download progress (schema X of Y)
    - Migration progress (current file being applied)
- **Summary Report**:
    - Total schemas processed
    - Migration versions applied per schema
    - Success/failure counts
    - Execution time per schema and total
    - Overall status
- **Detailed Logging** (verbose mode):
    - Individual migration file names being applied
    - SQL statements being executed
    - Flyway internal logs (via SLF4J bridge)
    - HTTP request/response details

### Command-Line Interface

```bash
jbang FlywayProvisioner.java \
  --artifactory-base-url <base-url> \
  [--artifactory-user <username>] \
  [--artifactory-password <password>] \
  --schemas <schema1:version1,schema2:version2,...> \
  --db-url <jdbc-url> \
  --db-user <username> \
  --db-password <password> \
  [--baseline-location <path-to-baseline-scripts>] \
  [--work-dir <temp-directory>] \
  [--cleanup / --no-cleanup] \
  [--fail-fast / --continue-on-error] \
  [--verbose]
```

#### Parameters

- `--artifactory-base-url` (required): Base URL for migration artifacts
- `--artifactory-user`: HTTP basic auth username (optional)
- `--artifactory-password`: HTTP basic auth password (optional)
- `--schemas` (required): Comma-separated schema:version pairs
- `--db-url` (required): JDBC connection URL (e.g., `jdbc:postgresql://localhost:5432/mydb`)
- `--db-user` (required): Database username (must have superuser or schema creation privileges)
- `--db-password` (required): Database password
- `--baseline-location`: Path to baseline SQL scripts (default: `./baseline-scripts`)
- `--work-dir`: Temporary directory for downloads and extraction (default: system temp)
- `--cleanup`: Remove temporary files after execution (default: true)
- `--fail-fast`: Stop on first failure (default: false, continues with remaining schemas)
- `--verbose` / `-v`: Enable debug logging

### Execution Flow

1. **Parse Input**
    - Process command-line arguments
    - Validate required parameters
    - Parse schema:version mappings

2. **Initialize Environment**
    - Configure tinylog based on verbose flag
    - Set up working directory
    - Validate baseline scripts location

3. **Download Artifacts**
    - For each schema:version pair:
        - Construct download URL
        - Download ZIP file using OkHttp
        - Save to working directory
        - Report progress

4. **Extract Files**
    - Unzip all downloaded artifacts
    - Organize extracted files by schema
    - Validate Flyway migration file structure

5. **Execute Baseline**
    - Connect to database
    - For each schema:
        - Run baseline SQL scripts from static assets
        - Create schema, users, and base permissions
        - Log execution details

6. **Run Migrations**
    - For each schema:
        - Configure Flyway instance
        - Set migration location to extracted files
        - Execute Flyway baseline (to initialize schema_history)
        - Execute Flyway migrate
        - Capture migration results
        - Report applied versions

7. **Generate Report**
    - Compile execution statistics
    - Display summary table
    - Show success/failure status for each schema
    - Report total execution time

8. **Cleanup**
    - Close database connections
    - Remove temporary files (if --cleanup enabled)
    - Exit with appropriate status code

### Example Usage

```bash
# Basic usage
jbang FlywayProvisioner.java \
  --artifactory-base-url https://artifactory.company.com/migrations \
  --schemas customer:1.5.0,orders:2.3.1,inventory:1.0.0 \
  --db-url jdbc:postgresql://localhost:5432/mydb \
  --db-user postgres \
  --db-password ${DB_PASSWORD}

# With authentication and verbose logging
jbang FlywayProvisioner.java \
  --artifactory-base-url https://artifactory.company.com/migrations \
  --artifactory-user deploy-user \
  --artifactory-password ${ARTIFACTORY_TOKEN} \
  --schemas customer:1.5.0,orders:2.3.1 \
  --db-url jdbc:postgresql://db-server:5432/mydb \
  --db-user postgres \
  --db-password ${DB_PASSWORD} \
  --baseline-location ./baseline-scripts \
  --verbose

# Fail-fast mode with custom work directory
jbang FlywayProvisioner.java \
  --artifactory-base-url https://artifactory.company.com/migrations \
  --schemas customer:1.5.0 \
  --db-url jdbc:postgresql://localhost:5432/mydb \
  --db-user postgres \
  --db-password password123 \
  --work-dir /tmp/flyway-work \
  --fail-fast \
  --verbose
```

---

## Architectural Guidelines

### JBang Script Structure

Each script is a single `.java` file with inline JBang directives for dependencies.

**JBang Header Example**:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS info.picocli:picocli:4.7.5
//DEPS org.flywaydb:flyway-core:10.4.1
//DEPS org.flywaydb:flyway-database-postgresql:10.4.1
//DEPS org.postgresql:postgresql:42.7.1
//DEPS com.squareup.okhttp3:okhttp:4.12.0
//DEPS org.tinylog:tinylog-api:2.6.2
//DEPS org.tinylog:tinylog-impl:2.6.2
//DEPS org.tinylog:slf4j-tinylog:2.6.2
//DEPS com.jcraft:jsch:0.1.55

//JAVA 17+

import picocli.CommandLine;
import picocli.CommandLine.*;
// ... other imports
```

### Static Nested Classes Organization

Organize functionality using static nested classes to maintain single-file design while ensuring clean separation of
concerns.

**Example Structure**:

```java

@Command(name = "flyway-provisioner",
        mixinStandardHelpOptions = true,
        description = "Automates PostgreSQL schema provisioning via Flyway")
public class FlywayProvisioner implements Callable<Integer> {

    static {
        // Configure tinylog programmatically
        configureTinylog();
    }

    // CLI Parameters
    @Option(names = "--schemas", required = true)
    private String schemasInput;

    @Option(names = {"-v", "--verbose"})
    private boolean verbose;

    // Main execution
    public static void main(String[] args) {
        int exitCode = new CommandLine(new FlywayProvisioner())
                .setDefaultValueProvider(new PropertiesDefaultProvider())
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // Main orchestration logic
        var schemas = SchemaParser.parse(schemasInput);
        var downloader = new ArtifactDownloader(artifactoryBaseUrl);
        var manager = new DatabaseManager(dbUrl, dbUser, dbPassword);

        // Execute workflow
        // ...

        return 0; // success
    }

    // Nested class for HTTP operations
    static class ArtifactDownloader {
        private final OkHttpClient client;
        private final String baseUrl;

        ArtifactDownloader(String baseUrl) {
            this.baseUrl = baseUrl;
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .readTimeout(Duration.ofMinutes(5))
                    .build();
        }

        Path downloadArtifact(String schema, String version, Path workDir) {
            // Download logic using OkHttp
        }

        void extractZip(Path zipFile, Path destination) {
            // Extraction logic
        }
    }

    // Nested class for database operations
    static class DatabaseManager {
        private final String jdbcUrl;
        private final String username;
        private final String password;

        void executeBaseline(String schema, Path baselineScriptsDir) {
            // Execute baseline SQL scripts
        }

        MigrationResult runMigrations(String schema, Path migrationLocation) {
            // Configure and run Flyway
            var flyway = Flyway.configure()
                    .dataSource(jdbcUrl, username, password)
                    .schemas(schema)
                    .locations("filesystem:" + migrationLocation)
                    .load();

            flyway.baseline();
            return flyway.migrate();
        }
    }

    // Nested class for progress reporting
    static class ExecutionReporter {
        void logProgress(String message) {
            // Log with appropriate level
        }

        void generateSummary(List<SchemaResult> results) {
            // Format and display summary table
        }
    }

    // Record for schema information (Java 17 feature)
    record SchemaVersion(String name, String version) {
    }

    // Record for migration results
    record SchemaResult(String schema, String version, boolean success,
                        int migrationsApplied, long executionTimeMs,
                        String errorMessage) {
    }

    // Utility class for parsing
    static class SchemaParser {
        static List<SchemaVersion> parse(String input) {
            // Parse "schema1:version1,schema2:version2" format
        }
    }

    // Configure tinylog
    private static void configureTinylog() {
        // Programmatic configuration of tinylog
        // Set pattern: %d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] %logger{36} - %msg%n
    }
}
```

### Abstraction Levels

Maintain clear separation between abstraction levels:

1. **High-Level** (Main class, `call()` method):
    - Orchestration logic
    - Parameter validation
    - Error handling and reporting
    - Overall workflow coordination

2. **Mid-Level** (Nested classes):
    - Feature-specific operations (download, migrate, report)
    - Business logic implementation
    - Resource management (connections, files)

3. **Low-Level** (Private methods in nested classes):
    - Utility functions (file I/O, string parsing, validation)
    - Data transformation
    - Primitive operations

### Picocli Best Practices

#### Mixins for Reusable Parameters

```java
// Common parameters mixin
static class CommonOptions {
    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose/debug output")
    boolean verbose;

    @Option(names = {"-h", "--help"},
            usageHelp = true,
            description = "Display this help message")
    boolean helpRequested;
}

// Use mixin in command
@Command(name = "my-command")
class MyCommand implements Callable<Integer> {
    @Mixin
    CommonOptions common;

    // Enable mixin help
    @Spec
    CommandSpec spec;
}
```

#### Properties Default Provider

```java
// application.properties
db.url=jdbc:postgresql://localhost:5432/mydb
db.url.description=
JDBC connection
URL for
PostgreSQL database
db.user=postgres
db.user.description=
Database username
work.dir=/tmp/flyway-work
work.dir.description=
Temporary directory for
downloads and
extraction

// Usage in code
new

CommandLine(new FlywayProvisioner())
        .

setDefaultValueProvider(new PropertiesDefaultProvider())
        .

execute(args);
```

---

## Error Handling

### Requirements

- Graceful degradation on non-critical failures
- Clear error messages with actionable information
- Proper exception propagation and logging
- Cleanup on failure (temporary files, connections)

### Example Scenarios

- SSH connection timeout → Retry with backoff
- Artifactory download failure → Report and skip schema
- Migration failure → Rollback and report detailed error
- Invalid parameters → Show usage help

---

## Logging and Reporting

### Tinylog Configuration

#### Programmatic Setup

Configure tinylog via static initializer or Java code (not properties file):

```java
static {
    // Configure tinylog programmatically
    Map<String, String> config = new HashMap<>();
    config.put("writer", "console");
    config.put("writer.format", "{date: yyyy-MM-dd HH:mm:ss} {level} [{thread}] {class} - {message}");
    config.put("writer.level", determineLogLevel()); // INFO or DEBUG based on --verbose

    // Apply configuration
    Configuration.replace(config);
}

private static String determineLogLevel() {
    // Check if --verbose is in args before picocli parsing
    return Arrays.asList(System.getProperty("user.args", "").split(" "))
            .contains("--verbose") ? "debug" : "info";
}
```

#### SLF4J Bridge for Flyway

Include `slf4j-tinylog` dependency to capture Flyway's logging output:

```java
//DEPS org.tinylog:slf4j-tinylog:2.6.2
```

This ensures Flyway's migration file names and SQL execution logs are visible in verbose mode.

### Log Levels

**INFO Level (Standard Mode)**:

- Script startup and parameter summary
- Major workflow steps (downloading, extracting, migrating)
- Progress indicators (schema X of Y)
- Success/failure confirmations
- Final summary report

**DEBUG Level (Verbose Mode)**:

- Detailed parameter values
- HTTP request/response details (URLs, status codes, headers)
- SSH commands being executed
- SQL statements being run
- Individual migration file names (via Flyway SLF4J logging)
- Timing information for each operation
- Temporary file paths and cleanup activities

### Output Format

#### Standard Output Examples

**Progress Indicators**:

```
2026-01-08 10:15:23 INFO  [main] FlywayProvisioner - Starting provisioning for 3 schemas
2026-01-08 10:15:24 INFO  [main] ArtifactDownloader - Downloading schema 'customer' v1.5.0...
2026-01-08 10:15:28 INFO  [main] ArtifactDownloader - ✓ Downloaded customer-1.5.0.zip (2.3 MB)
2026-01-08 10:15:29 INFO  [main] DatabaseManager - Executing baseline for schema 'customer'...
2026-01-08 10:15:30 INFO  [main] DatabaseManager - ✓ Baseline completed for 'customer'
2026-01-08 10:15:31 INFO  [main] DatabaseManager - Running migrations for 'customer'...
2026-01-08 10:16:15 INFO  [main] DatabaseManager - ✓ Applied 12 migrations to 'customer' (44.2s)
```

**Error Notifications**:

```
2026-01-08 10:20:45 ERROR [main] ArtifactDownloader - ✗ Failed to download schema 'inventory' v1.0.0
2026-01-08 10:20:45 ERROR [main] ArtifactDownloader -   HTTP 404: Not Found
2026-01-08 10:20:45 ERROR [main] ArtifactDownloader -   URL: https://artifactory.company.com/migrations/inventory/inventory-1.0.0.zip
2026-01-08 10:20:45 WARN  [main] FlywayProvisioner - Skipping schema 'inventory', continuing with remaining schemas
```

#### Verbose Mode Examples

```
2026-01-08 10:15:24 DEBUG [main] ArtifactDownloader - Constructed download URL: https://artifactory.company.com/migrations/customer/customer-1.5.0.zip
2026-01-08 10:15:24 DEBUG [main] ArtifactDownloader - Sending HTTP GET request...
2026-01-08 10:15:24 DEBUG [main] ArtifactDownloader - Response: 200 OK, Content-Length: 2,456,789
2026-01-08 10:15:28 DEBUG [main] ArtifactDownloader - Downloaded to: /tmp/flyway-work/customer-1.5.0.zip
2026-01-08 10:15:28 DEBUG [main] ArtifactDownloader - Extracting ZIP to: /tmp/flyway-work/customer/
2026-01-08 10:15:29 DEBUG [main] ArtifactDownloader - Extracted 45 files
2026-01-08 10:15:30 DEBUG [main] DatabaseManager - Executing SQL: CREATE SCHEMA IF NOT EXISTS customer
2026-01-08 10:15:30 DEBUG [main] DatabaseManager - Executing SQL: CREATE USER customer_user WITH PASSWORD '***'
2026-01-08 10:15:31 DEBUG [main] FlywayMigration - Flyway baseline set to version 0
2026-01-08 10:15:32 DEBUG [main] FlywayMigration - Applying V1__initial_schema.sql
2026-01-08 10:15:33 DEBUG [main] FlywayMigration - Applying V1.1__add_customers_table.sql
...
```

### Summary Report Format

Display a well-formatted summary at the end of execution:

```
=================================================
  Flyway Migration Execution Summary
=================================================
Total Schemas: 3
Successful: 2
Failed: 1
Total Execution Time: 125.3s

Details:
  ✓ customer (v1.5.0) - 45.2s - 12 migrations applied
  ✓ orders (v2.3.1) - 67.1s - 23 migrations applied
  ✗ inventory (v1.0.0) - FAILED - HTTP 404: Artifact not found
  
=================================================
```

**Verbose Summary** (includes applied file names):

```
=================================================
  Flyway Migration Execution Summary
=================================================
Total Schemas: 3
Successful: 2
Failed: 1
Total Execution Time: 125.3s

Details:
  ✓ customer (v1.5.0) - 45.2s - 12 migrations applied
      V1__initial_schema.sql
      V1.1__add_customers_table.sql
      V1.2__add_indexes.sql
      V2__add_address_fields.sql
      ... (8 more files)
      
  ✓ orders (v2.3.1) - 67.1s - 23 migrations applied
      V1__initial_schema.sql
      V1.1__add_orders_table.sql
      ... (21 more files)
      
  ✗ inventory (v1.0.0) - FAILED
      Error: HTTP 404: Artifact not found
      URL: https://artifactory.company.com/migrations/inventory/inventory-1.0.0.zip
      
=================================================
```

### Progress Indicators

Use visual indicators for better UX:

- ✓ (checkmark) for success
- ✗ (cross) for failure
- ⚠ (warning) for non-critical issues
- → (arrow) for in-progress operations

---

## Configuration Management

### Configuration Sources (Priority Order)

1. **Command-Line Arguments** (highest priority)
2. **Environment Variables** (via `${VAR_NAME}` interpolation)
3. **Properties File** (via Picocli's `PropertiesDefaultProvider`)
4. **Hardcoded Defaults** (lowest priority)

### Properties File Format

**File**: `application.properties`

```properties
# Flyway Provisioner Configuration
# Artifactory settings
artifactory.base.url=https://artifactory.company.com/migrations
artifactory.base.url.description=Base URL for downloading migration artifacts
# Database connection
db.url=jdbc:postgresql://localhost:5432/mydb
db.url.description=JDBC connection URL for PostgreSQL database
db.user=postgres
db.user.description=Database username with schema creation privileges
db.password=${DB_PASSWORD}
db.password.description=Database password (supports environment variable interpolation)
# File paths
baseline.location=./baseline-scripts
baseline.location.description=Directory containing baseline SQL scripts
work.dir=/tmp/flyway-work
work.dir.description=Temporary directory for downloads and extraction
# Execution options
cleanup=true
cleanup.description=Remove temporary files after execution
fail.fast=false
fail.fast.description=Stop execution on first failure (false = continue with remaining schemas)
# SSH Configurator settings
ssh.port=22
ssh.port.description=SSH port number
pg.config.path=/etc/postgresql/15/main
pg.config.path.description=PostgreSQL configuration directory path
pg.version=15
pg.version.description=PostgreSQL major version
```

### Environment Variable Interpolation

Support `${ENV_VAR_NAME}` syntax in:

- Command-line arguments
- Properties file values
- Direct parameter values

**Example**:

```bash
jbang FlywayProvisioner.java \
  --db-password ${DB_PASSWORD} \
  --artifactory-password ${ARTIFACTORY_TOKEN}
```

Properties file:

```properties
db.password=${DB_PASSWORD}
artifactory.password=${ARTIFACTORY_TOKEN}
```

### Picocli Integration

```java
// Enable default value provider
public static void main(String[] args) {
    int exitCode = new CommandLine(new FlywayProvisioner())
            .setDefaultValueProvider(new PropertiesDefaultProvider())
            .execute(args);
    System.exit(exitCode);
}

// Parameters with descriptions (shown in --help)
@Option(names = "--db-url",
        required = true,
        description = "JDBC connection URL for PostgreSQL database")
private String dbUrl;
```

---

## Integration Testing

### Overview

Focus on integration testing with real services running in Docker containers. No unit tests required.

### Testing Infrastructure

#### Docker Compose Setup

Create a `docker-compose.yml` for the testing environment:

```yaml
version: '3.8'

services:
  # PostgreSQL 15 with SSH access
  postgres-ssh:
    image: custom-postgres-ssh:latest
    build:
      context: ./docker/postgres-ssh
      dockerfile: Dockerfile
    ports:
      - "5432:5432"
      - "2222:22"
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: testpass
      POSTGRES_DB: testdb
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U postgres" ]
      interval: 5s
      timeout: 5s
      retries: 5

  # Mock Artifactory (HTTP server with ZIP files)
  mock-artifactory:
    image: nginx:alpine
    ports:
      - "8080:80"
    volumes:
      - ./test-artifacts:/usr/share/nginx/html/migrations:ro
    healthcheck:
      test: [ "CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost/" ]
      interval: 5s
      timeout: 5s
      retries: 3

volumes:
  postgres-data:
```

#### Custom PostgreSQL + SSH Image

**Dockerfile** (`./docker/postgres-ssh/Dockerfile`):

```dockerfile
FROM postgres:15

# Install SSH server
RUN apt-get update && \
    apt-get install -y openssh-server sudo && \
    mkdir /var/run/sshd && \
    rm -rf /var/lib/apt/lists/*

# Create SSH user with sudo privileges
RUN useradd -m -s /bin/bash testuser && \
    echo 'testuser:testpass' | chpasswd && \
    usermod -aG sudo testuser && \
    echo 'testuser ALL=(ALL) NOPASSWD: ALL' >> /etc/sudoers

# Configure SSH
RUN sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config && \
    sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config

# Expose SSH port
EXPOSE 22

# Start script to run both PostgreSQL and SSH
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["postgres"]
```

**Entry Point Script** (`./docker/postgres-ssh/docker-entrypoint.sh`):

```bash
#!/bin/bash
set -e

# Start SSH daemon
/usr/sbin/sshd

# Execute original postgres entrypoint
exec docker-entrypoint.sh "$@"
```

#### Test Artifacts Structure

Create mock migration artifacts in `./test-artifacts/`:

```
test-artifacts/
├── customer/
│   └── customer-1.5.0.zip
├── orders/
│   └── orders-2.3.1.zip
└── inventory/
    └── inventory-1.0.0.zip
```

Each ZIP file should contain Flyway migration files:

```
customer-1.5.0.zip:
  └── V1__initial_schema.sql
  └── V1.1__add_customers_table.sql
  └── V1.2__add_indexes.sql
  └── V2__add_address_fields.sql
  └── ...
```

### Test Scenarios

#### Test 1: PostgreSQL SSH Configuration

**Objective**: Verify SSH connection and PostgreSQL configuration

```bash
# Start test environment
docker-compose up -d postgres-ssh

# Wait for services to be healthy
docker-compose ps

# Run SSH configurator
jbang PostgresSshConfigurator.java \
  --host localhost \
  --port 2222 \
  --user testuser \
  --password testpass \
  --verbose

# Verify: Check if postgresql.conf was modified
docker exec postgres-ssh cat /etc/postgresql/15/main/postgresql.conf | grep listen_addresses

# Verify: Check if pg_hba.conf allows remote connections
docker exec postgres-ssh cat /etc/postgresql/15/main/pg_hba.conf

# Verify: Connect remotely to PostgreSQL
psql -h localhost -U postgres -d testdb -c "SELECT version();"
```

#### Test 2: Flyway Provisioner - Single Schema

**Objective**: Test basic migration with one schema

```bash
# Start test environment
docker-compose up -d

# Run provisioner
jbang FlywayProvisioner.java \
  --artifactory-base-url http://localhost:8080/migrations \
  --schemas customer:1.5.0 \
  --db-url jdbc:postgresql://localhost:5432/testdb \
  --db-user postgres \
  --db-password testpass \
  --baseline-location ./baseline-scripts \
  --verbose

# Verify: Check schema was created
psql -h localhost -U postgres -d testdb -c "\dn"

# Verify: Check migrations were applied
psql -h localhost -U postgres -d testdb -c "SELECT * FROM customer.flyway_schema_history;"

# Verify: Check migration results
psql -h localhost -U postgres -d testdb -c "SELECT version FROM customer.flyway_schema_history ORDER BY installed_rank;"
```

#### Test 3: Flyway Provisioner - Multiple Schemas

**Objective**: Test concurrent schema provisioning

```bash
jbang FlywayProvisioner.java \
  --artifactory-base-url http://localhost:8080/migrations \
  --schemas customer:1.5.0,orders:2.3.1,inventory:1.0.0 \
  --db-url jdbc:postgresql://localhost:5432/testdb \
  --db-user postgres \
  --db-password testpass \
  --baseline-location ./baseline-scripts \
  --verbose

# Verify: All schemas exist
psql -h localhost -U postgres -d testdb -c "\dn"

# Verify: Check each schema's migration history
for schema in customer orders inventory; do
  echo "Checking $schema..."
  psql -h localhost -U postgres -d testdb -c "SELECT COUNT(*) FROM $schema.flyway_schema_history;"
done
```

#### Test 4: Error Handling - Missing Artifact

**Objective**: Test graceful failure handling

```bash
jbang FlywayProvisioner.java \
  --artifactory-base-url http://localhost:8080/migrations \
  --schemas customer:1.5.0,nonexistent:99.99.99 \
  --db-url jdbc:postgresql://localhost:5432/testdb \
  --db-user postgres \
  --db-password testpass \
  --continue-on-error \
  --verbose

# Verify: Customer schema succeeded despite nonexistent failure
psql -h localhost -U postgres -d testdb -c "SELECT COUNT(*) FROM customer.flyway_schema_history;"
```

#### Test 5: Fail-Fast Mode

**Objective**: Test fail-fast behavior

```bash
jbang FlywayProvisioner.java \
  --artifactory-base-url http://localhost:8080/migrations \
  --schemas customer:1.5.0,nonexistent:99.99.99,orders:2.3.1 \
  --db-url jdbc:postgresql://localhost:5432/testdb \
  --db-user postgres \
  --db-password testpass \
  --fail-fast \
  --verbose

# Verify: Execution stopped after first failure (orders should not be processed)
# Check exit code: should be non-zero
echo $?
```

### Test Utilities

Create helper scripts for testing:

**`run-tests.sh`**:

```bash
#!/bin/bash
set -e

echo "Starting test environment..."
docker-compose up -d

echo "Waiting for services to be healthy..."
sleep 10

echo "Running Test 1: SSH Configuration..."
./tests/test-ssh-config.sh

echo "Running Test 2: Single Schema Migration..."
./tests/test-single-schema.sh

echo "Running Test 3: Multiple Schemas Migration..."
./tests/test-multiple-schemas.sh

echo "Running Test 4: Error Handling..."
./tests/test-error-handling.sh

echo "Running Test 5: Fail-Fast Mode..."
./tests/test-fail-fast.sh

echo "Cleaning up..."
docker-compose down -v

echo "All tests passed! ✓"
```

### Test Data Preparation

**Script to generate test artifacts** (`prepare-test-artifacts.sh`):

```bash
#!/bin/bash

ARTIFACTS_DIR="./test-artifacts"

# Create directories
mkdir -p $ARTIFACTS_DIR/{customer,orders,inventory}

# Generate sample migrations for customer
mkdir -p /tmp/customer-migrations
cat > /tmp/customer-migrations/V1__initial_schema.sql <<EOF
CREATE TABLE customers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
);
EOF

cat > /tmp/customer-migrations/V1.1__add_indexes.sql <<EOF
CREATE INDEX idx_customers_email ON customers(email);
EOF

# Zip migrations
cd /tmp/customer-migrations
zip -r $ARTIFACTS_DIR/customer/customer-1.5.0.zip .
cd -

# Repeat for orders and inventory...
# (Similar pattern)

echo "Test artifacts prepared!"
```

---

## Deliverables

### 1. Core JBang Scripts

- **PostgresSshConfigurator.java** - SSH configuration script with inline dependencies
- **FlywayProvisioner.java** - Migration orchestration script with inline dependencies

### 2. Configuration Files

- **application.properties** - Default configuration with descriptions
- Example properties files for different environments (dev, staging, prod)

### 3. Baseline SQL Scripts

Static baseline scripts bundled with the project in `./baseline-scripts/`:

```
baseline-scripts/
├── 01_create_schema_template.sql
├── 02_create_user_template.sql
├── 03_grant_permissions_template.sql
└── README.md
```

**Example**: `01_create_schema_template.sql`

```sql
-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS ${schema_name};

-- Set default search path
ALTER
DATABASE
${database_name}
SET
search_path
TO
${schema_name},
public;
```

**Example**: `02_create_user_template.sql`

```sql
-- Create user for schema
CREATE
USER ${schema_name}_user WITH PASSWORD '${schema_password}';

-- Grant schema ownership
GRANT ALL PRIVILEGES ON SCHEMA
${schema_name} TO ${schema_name}_user;
```

**Example**: `03_grant_permissions_template.sql`

```sql
-- Grant default privileges for future objects
ALTER
DEFAULT PRIVILEGES IN SCHEMA
${schema_name}
GRANT
ALL
ON
TABLES
TO
${
schema_name
}
_user;

ALTER
DEFAULT PRIVILEGES IN SCHEMA
${schema_name}
GRANT
ALL
ON
SEQUENCES
TO
${
schema_name
}
_user;

ALTER
DEFAULT PRIVILEGES IN SCHEMA
${schema_name}
GRANT
EXECUTE
ON
FUNCTIONS
TO
${
schema_name
}
_user;
```

### 4. Integration Testing Infrastructure

#### Docker Setup

- **docker-compose.yml** - Complete testing environment
- **docker/postgres-ssh/Dockerfile** - Custom PostgreSQL + SSH image
- **docker/postgres-ssh/docker-entrypoint.sh** - Startup script

#### Test Scripts

- **run-tests.sh** - Master test runner
- **prepare-test-artifacts.sh** - Generate test migration ZIPs
- **tests/test-ssh-config.sh** - SSH configuration tests
- **tests/test-single-schema.sh** - Single schema migration tests
- **tests/test-multiple-schemas.sh** - Multiple schema tests
- **tests/test-error-handling.sh** - Error handling tests
- **tests/test-fail-fast.sh** - Fail-fast mode tests

#### Test Artifacts

```
test-artifacts/
├── customer/
│   └── customer-1.5.0.zip
├── orders/
│   └── orders-2.3.1.zip
└── inventory/
    └── inventory-1.0.0.zip
```

### 5. Documentation

#### README.md

Comprehensive usage documentation including:

- **Prerequisites**: JBang installation, Java 17+, Docker (for testing)
- **Installation**: How to run the scripts
- **Quick Start**: Basic usage examples
- **Configuration**: Detailed configuration options
- **Examples**: Common use cases
- **Troubleshooting**: Common issues and solutions
- **Integration Testing**: How to run tests

#### Example README Structure:

```markdown
# PostgreSQL Provisioner

## Overview

Brief description of the tools and their purpose.

## Prerequisites

- JBang 0.115.0+
- Java 17+
- Docker & Docker Compose (for integration testing)

## Installation

```bash
# Install JBang
curl -Ls https://sh.jbang.dev | bash -s - app setup
```

## Quick Start

### SSH Configuration

```bash
jbang PostgresSshConfigurator.java \
  --host db-server.company.com \
  --user admin \
  --password ${SSH_PASSWORD} \
  --verbose
```

### Flyway Provisioning

```bash
jbang FlywayProvisioner.java \
  --artifactory-base-url https://artifactory.company.com/migrations \
  --schemas customer:1.5.0,orders:2.3.1 \
  --db-url jdbc:postgresql://db-server:5432/mydb \
  --db-user postgres \
  --db-password ${DB_PASSWORD} \
  --verbose
```

## Configuration

Detailed explanation of all parameters and configuration options.

## Integration Testing

Instructions for running the test suite.

## Troubleshooting

Common issues and solutions.

```

### 6. Project Structure

Complete project layout:
```

postgres-provisioner/
├── PostgresSshConfigurator.java # Script 1
├── FlywayProvisioner.java # Script 2
├── application.properties # Default configuration
├── README.md # Main documentation
│
├── baseline-scripts/ # Static baseline SQL
│ ├── 01_create_schema_template.sql
│ ├── 02_create_user_template.sql
│ ├── 03_grant_permissions_template.sql
│ └── README.md
│
├── config/ # Example configurations
│ ├── dev.properties
│ ├── staging.properties
│ └── prod.properties
│
├── docker/ # Docker setup
│ ├── docker-compose.yml
│ └── postgres-ssh/
│ ├── Dockerfile
│ └── docker-entrypoint.sh
│
├── test-artifacts/ # Test migration ZIPs
│ ├── customer/
│ │ └── customer-1.5.0.zip
│ ├── orders/
│ │ └── orders-2.3.1.zip
│ └── inventory/
│ └── inventory-1.0.0.zip
│
├── tests/ # Integration test scripts
│ ├── test-ssh-config.sh
│ ├── test-single-schema.sh
│ ├── test-multiple-schemas.sh
│ ├── test-error-handling.sh
│ └── test-fail-fast.sh
│
├── run-tests.sh # Test runner
└── prepare-test-artifacts.sh # Test data generator

```

---

## Dependencies

All dependencies are managed via JBang's `//DEPS` directives (no Maven/Gradle needed).

### Core Dependencies

#### Both Scripts
```java
//DEPS info.picocli:picocli:4.7.5              // CLI framework
//DEPS org.tinylog:tinylog-api:2.6.2           // Logging API
//DEPS org.tinylog:tinylog-impl:2.6.2          // Logging implementation
//DEPS org.tinylog:slf4j-tinylog:2.6.2         // SLF4J bridge for Flyway
```

#### PostgresSshConfigurator.java

```java
//DEPS com.jcraft:jsch:0.1.55                  // SSH client
// OR
//DEPS org.apache.sshd:sshd-core:2.11.0        // Alternative SSH client (more modern)
```

#### FlywayProvisioner.java

```java
//DEPS org.flywaydb:flyway-core:10.4.1                   // Flyway migrations
//DEPS org.flywaydb:flyway-database-postgresql:10.4.1    // PostgreSQL support
//DEPS org.postgresql:postgresql:42.7.1                  // JDBC driver
//DEPS com.squareup.okhttp3:okhttp:4.12.0                // HTTP client
```

### Dependency Details

| Library                    | Purpose                    | Notes                                                    |
|----------------------------|----------------------------|----------------------------------------------------------|
| **Picocli 4.7.5**          | Command-line parsing       | Handles args, mixins, default providers, help generation |
| **Tinylog 2.6.2**          | Logging framework          | Lightweight, programmatically configurable               |
| **SLF4J-Tinylog Bridge**   | Flyway logging integration | Captures Flyway's SLF4J logs in verbose mode             |
| **JSch 0.1.55**            | SSH client                 | Simple, widely used (if choosing JSch)                   |
| **Apache SSHD 2.11.0**     | SSH client                 | More modern alternative to JSch                          |
| **Flyway Core 10.4.1**     | Database migrations        | Latest stable version with PostgreSQL 15 support         |
| **PostgreSQL JDBC 42.7.1** | Database connectivity      | PostgreSQL JDBC driver                                   |
| **OkHttp 4.12.0**          | HTTP client                | Simple, efficient HTTP downloads                         |

### Complete JBang Headers

**PostgresSshConfigurator.java**:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//DEPS info.picocli:picocli:4.7.5
//DEPS org.tinylog:tinylog-api:2.6.2
//DEPS org.tinylog:tinylog-impl:2.6.2
//DEPS com.jcraft:jsch:0.1.55

import picocli.CommandLine;
import picocli.CommandLine.*;
import com.jcraft.jsch.*;
import org.tinylog.Logger;
// ... other imports
```

**FlywayProvisioner.java**:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//DEPS info.picocli:picocli:4.7.5
//DEPS org.flywaydb:flyway-core:10.4.1
//DEPS org.flywaydb:flyway-database-postgresql:10.4.1
//DEPS org.postgresql:postgresql:42.7.1
//DEPS com.squareup.okhttp3:okhttp:4.12.0
//DEPS org.tinylog:tinylog-api:2.6.2
//DEPS org.tinylog:tinylog-impl:2.6.2
//DEPS org.tinylog:slf4j-tinylog:2.6.2

import picocli.CommandLine;
import picocli.CommandLine.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import okhttp3.*;
import org.tinylog.Logger;
import org.tinylog.configuration.Configuration;
// ... other imports
```

### Why These Libraries?

**Picocli**: Industry-standard CLI framework with excellent annotation-based API, mixins, and default value providers.

**Tinylog**: Lightweight logging framework that can be configured programmatically (no XML/properties files needed),
perfect for single-file scripts.

**OkHttp**: Modern, efficient HTTP client with simple API, better than Apache HttpClient for this use case.

**JSch vs Apache SSHD**: JSch is simpler and lighter, Apache SSHD is more modern and actively maintained. Choose based
on preference (spec allows either).

**Flyway 10.x**: Latest stable version with excellent PostgreSQL support and programmatic configuration.

---

## Success Criteria

### Functional Requirements

- ✅ **PostgresSshConfigurator**: Successfully connects via SSH and configures PostgreSQL for remote access
- ✅ **FlywayProvisioner**: Downloads, extracts, and applies migrations for multiple schemas
- ✅ **CLI Arguments**: All command-line options work as specified
- ✅ **Properties Files**: Default values loaded from `application.properties`
- ✅ **Environment Variables**: `${VAR_NAME}` interpolation works in all contexts
- ✅ **Verbose Mode**: Provides detailed debug logs including Flyway migration files
- ✅ **Error Handling**: Gracefully handles common failure scenarios with clear messages
- ✅ **Fail-Fast Mode**: Stops on first error when enabled
- ✅ **Continue-On-Error**: Processes remaining schemas when one fails
- ✅ **Summary Reports**: Clear, well-formatted execution summaries

### Code Quality

- ✅ **Single-File Design**: Each script is self-contained with all logic in one `.java` file
- ✅ **Static Nested Classes**: Clean separation of concerns using nested classes
- ✅ **Small Methods**: Each method represents a single abstraction level
- ✅ **Java 17 Features**: Uses records, text blocks, modern syntax
- ✅ **Self-Documenting**: Clear naming and comprehensive inline comments
- ✅ **JBang Integration**: Proper `//DEPS` directives and shebang

### Logging and Reporting

- ✅ **Tinylog Configuration**: Programmatically configured with specified format
- ✅ **Log Levels**: INFO (standard) and DEBUG (verbose) work correctly
- ✅ **SLF4J Bridge**: Flyway logs appear in verbose mode
- ✅ **Progress Indicators**: Visual feedback during execution (✓, ✗, →)
- ✅ **Summary Reports**: Well-formatted tables with statistics
- ✅ **File Names in Verbose**: Migration file names shown when verbose enabled

### Database Operations

- ✅ **Baseline Execution**: Static SQL scripts create schemas, users, permissions
- ✅ **Flyway Migrations**: Migrations apply correctly to PostgreSQL database
- ✅ **Multiple Schemas**: Handles multiple schemas in single execution
- ✅ **Version Tracking**: Flyway schema_history tables created and populated
- ✅ **Permissions**: Future objects automatically accessible via default grants

### SSH Configuration

- ✅ **Remote Connection**: SSH connects successfully to remote server
- ✅ **Config Backup**: Creates timestamped backups before modifications
- ✅ **PostgreSQL Config**: Modifies `postgresql.conf` and `pg_hba.conf` correctly
- ✅ **Service Restart**: Safely restarts PostgreSQL service
- ✅ **Connection Verify**: Confirms PostgreSQL accepts remote connections

### Integration Testing

- ✅ **Docker Environment**: PostgreSQL 15 with SSH runs in Docker
- ✅ **Mock Artifactory**: HTTP server serves test migration ZIPs
- ✅ **Test Scripts**: All integration tests pass
- ✅ **Test Coverage**: Tests cover success, failure, and edge cases

### User Experience

- ✅ **Help Text**: `--help` displays clear, comprehensive usage information
- ✅ **Error Messages**: Actionable error messages with context
- ✅ **Exit Codes**: Proper exit codes (0 = success, non-zero = failure)
- ✅ **Execution Time**: Reports execution duration
- ✅ **Visual Feedback**: Clear progress indicators and status symbols 