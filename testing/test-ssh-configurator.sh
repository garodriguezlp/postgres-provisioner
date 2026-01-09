#!/bin/bash
# End-to-end test for PostgresSshConfigurator

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=================================================="
echo "  PostgresSshConfigurator End-to-End Test"
echo "=================================================="
echo ""

# Step 1: Verify wrong configuration is in place
echo "Step 1: Verifying PostgreSQL has incorrect configuration..."
echo "------------------------------------------------------"
CURRENT_CONFIG=$(docker exec testing-postgres-ssh-1 bash -c "grep -E '^listen_addresses|^shared_buffers' /var/lib/postgresql/data/postgresql.conf")
echo "$CURRENT_CONFIG"

if echo "$CURRENT_CONFIG" | grep -q "listen_addresses = 'localhost'" && \
   echo "$CURRENT_CONFIG" | grep -q "shared_buffers = 128MB"; then
    echo "✓ Incorrect configuration confirmed"
    echo "  - listen_addresses = 'localhost' (should be '*')"
    echo "  - shared_buffers = 128MB (should be 256MB)"
else
    echo "✗ Expected incorrect configuration not found!"
    exit 1
fi
echo ""

# Step 2: Run PostgresSshConfigurator
echo "Step 2: Running PostgresSshConfigurator..."
echo "------------------------------------------------------"
cd "$SCRIPT_DIR/.."
jbang PostgresSshConfigurator.java \
    --host localhost \
    --port 2222 \
    --user testuser \
    --password testpass \
    --verbose

echo ""

# Step 3: Verify configuration was fixed
echo "Step 3: Verifying PostgreSQL configuration was fixed..."
echo "------------------------------------------------------"
cd "$SCRIPT_DIR"

# Restart container to ensure config is applied
echo "Restarting container to apply configuration..."
docker restart testing-postgres-ssh-1
sleep 8

FIXED_CONFIG=$(docker exec testing-postgres-ssh-1 bash -c "grep -E '^listen_addresses|^shared_buffers' /var/lib/postgresql/data/postgresql.conf")
echo "$FIXED_CONFIG"

if echo "$FIXED_CONFIG" | grep -q "listen_addresses = '\*'" && \
   echo "$FIXED_CONFIG" | grep -q "shared_buffers = 256MB"; then
    echo "✓ Configuration successfully fixed!"
    echo "  - listen_addresses = '*' ✓"
    echo "  - shared_buffers = 256MB ✓"
else
    echo "✗ Configuration was not fixed correctly!"
    exit 1
fi
echo ""

# Step 4: Verify PostgreSQL is listening on all addresses
echo "Step 4: Verifying PostgreSQL is listening on all addresses..."
echo "------------------------------------------------------"
LISTEN_STATUS=$(docker logs testing-postgres-ssh-1 2>&1 | grep "listening on IPv4" | tail -1)
echo "$LISTEN_STATUS"

if echo "$LISTEN_STATUS" | grep -q "0.0.0.0"; then
    echo "✓ PostgreSQL is listening on all addresses (0.0.0.0)"
else
    echo "✗ PostgreSQL is not listening on all addresses!"
    exit 1
fi
echo ""

# Step 5: Test remote connection
echo "Step 5: Testing remote database connection..."
echo "------------------------------------------------------"
CONNECTION_TEST=$(docker exec testing-postgres-ssh-1 bash -c "PGPASSWORD=testpass psql -h 0.0.0.0 -U postgres -d testdb -c 'SELECT current_database();'" 2>&1 | grep -A1 "current_database" | tail -1 | tr -d ' ')

if [ "$CONNECTION_TEST" = "testdb" ]; then
    echo "✓ Remote connection successful!"
    echo "  Connected to database: testdb"
else
    echo "✗ Remote connection failed!"
    exit 1
fi
echo ""

# Step 6: Verify backup files were created
echo "Step 6: Verifying backup files were created..."
echo "------------------------------------------------------"
BACKUP_FILES=$(docker exec testing-postgres-ssh-1 bash -c "ls -1 /var/lib/postgresql/data/*.backup 2>/dev/null | wc -l")

if [ "$BACKUP_FILES" -ge 2 ]; then
    echo "✓ Backup files created:"
    docker exec testing-postgres-ssh-1 bash -c "ls -lh /var/lib/postgresql/data/*.backup"
else
    echo "✗ Backup files not found!"
    exit 1
fi
echo ""

echo "=================================================="
echo "  ✓ All Tests Passed!"
echo "=================================================="
echo ""
echo "Summary:"
echo "  - PostgreSQL configuration was successfully updated via SSH"
echo "  - listen_addresses changed from 'localhost' to '*'"
echo "  - shared_buffers changed from 128MB to 256MB"
echo "  - PostgreSQL is accepting remote connections"
echo "  - Backup files were created before modification"
echo ""
