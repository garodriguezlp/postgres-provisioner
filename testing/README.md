# PostgreSQL Provisioner - Testing Infrastructure

This directory contains the complete testing infrastructure for the PostgreSQL Provisioner project.

## Directory Structure

```
testing/
├── docker-compose.yml              # Main compose file for test environment
├── postgres-ssh/                   # Custom PostgreSQL + SSH Docker image
│   ├── Dockerfile
│   └── docker-entrypoint.sh
├── mock-artifactory/               # Mock Artifactory server
│   └── Dockerfile                  # Nginx + builds migration ZIPs
├── test-artifacts/                 # Migration sources
│   ├── customer-migrations/        # Source SQL files
│   ├── orders-migrations/
│   └── inventory-migrations/
├── up.sh                          # Start environment
├── down.sh                        # Stop environment
├── reset.sh                       # Reset (remove all data)
├── logs.sh                        # View logs
├── status.sh                      # Check environment status
├── prepare-artifacts.sh           # Rebuild mock-artifactory with fresh artifacts
└── README.md                      # This file
```

**Note**: Baseline SQL scripts are located in the project root at `../baseline-scripts/`, not in the testing directory.

## Prerequisites

- Docker and Docker Compose installed
- Git Bash (MinGW) on Windows, or Bash on Linux/macOS
- `psql` client (optional, for testing database connections)

## Quick Start

### 1. Start the Environment

```bash
./up.sh
```

This will:
- Build the PostgreSQL + SSH Docker image
- Build the Mock Artifactory Docker image (with migration ZIPs)
- Start PostgreSQL (port 5432) and SSH (port 2222)
- Start Mock Artifactory (Nginx on port 8080) serving the built-in artifacts
- Wait for services to be healthy

### 2. Check Status

```bash
./status.sh
```

### 3. View Logs

```bash
# Follow logs for all services
./logs.sh

# View logs without following
./logs.sh --no-follow

# View logs for specific service
./logs.sh postgres-ssh
```

### 4. Stop the Environment

```bash
./down.sh
```

Data is preserved in Docker volumes. Use `./reset.sh` to remove all data.

### 5. Reset (Clean Slate)

```bash
./reset.sh
```

This removes all containers, volumes, and data. You'll need to run `./up.sh` again.

## Service Endpoints

Once the environment is running:

### PostgreSQL
- **Host**: localhost
- **Port**: 5432
- **Database**: testdb
- **User**: postgres
- **Password**: testpass

**Test connection**:
```bash
psql -h localhost -U postgres -d testdb
```

### SSH Access
- **Host**: localhost
- **Port**: 2222
- **User**: testuser
- **Password**: testpass

**Test connection**:
```bash
ssh -p 2222 testuser@localhost
```

### Mock Artifactory
- **URL**: http://localhost:8080/migrations

**Test endpoints**:
```bash
# List customer artifacts
curl http://localhost:8080/migrations/customer/

# Download customer artifact
curl -O http://localhost:8080/migrations/customer/customer-1.5.0.zip
```

## Testing the Scripts

### Test PostgresSshConfigurator

```bash
jbang ../PostgresSshConfigurator.java \
  --host localhost \
  --port 2222 \
  --user testuser \
  --password testpass \
  --verbose
```

### Test FlywayProvisioner

```bash
jbang ../FlywayProvisioner.java \
  --artifactory-base-url http://localhost:8080/migrations \
  --schemas customer:1.5.0,orders:2.3.1,inventory:1.0.0 \
  --db-url jdbc:postgresql://localhost:5432/testdb \
  --db-user postgres \
  --db-password testpass \
  --baseline-location ../baseline-scripts \
  --verbose
```

## Troubleshooting

### Services won't start

Check Docker is running:
```bash
docker ps
```

Check logs:
```bash
./logs.sh
```

### PostgreSQL not accepting connections

Wait a bit longer - it can take 10-15 seconds to initialize:
```bash
./status.sh
```

### Mock Artifactory returns 404
mock-artifactory Docker image. Try rebuilding:
```bash
./prepare-artifacts.sh
```

Or manually:
```bash
./down.sh
docker-compose build --no-cache mock-artifactory
docker-compose build --no-cache
./up.sh
```

### Permission denied on scripts

Make scripts executable:
```bash
chmod +x *.sh
```

### Ports already in use

Stop the environment and check what's using the ports:
```bash
./down.sh

# Check port 5432
netstat -an | grep 5432

# Check port 2222
netstat -an | grep 2222

# Check port 8080
netstat -an | grep 8080
```


### Change Nginx Version

Edit [docker-compose.yml](docker-compose.yml) and [mock-artifactory/Dockerfile](mock-artifactory/Dockerfile) to use a different Nginx version.
## Customization
mock-artifactory/Dockerfile` to copy and zip the new schema
4. Rebuild the image with `./prepare-artifacts.sh

Edit [docker-compose.yml](docker-compose.yml) and [postgres-ssh/Dockerfile](postgres-ssh/Dockerfile) to use a different PostgreSQL version.

### Add More Test Schemas

1. Create a new directory in `test-artifacts/` (e.g., `shipping-migrations/`)
2. Add Flyway migration files (V1__*.sql, V2__*.sql, etc.)
3. Update `postgres-ssh/Dockerfile` to copy and zip the new schema
4. Rebuild the image with `docker-compose build`

## Clean Up

To completely remove everything:

```bash
./reset.sh
```

This removes:
- All containers
- All volumes (database data)
- Docker images built for this project
