#!/bin/bash
# Test script for FlywayProvisioner with the testing infrastructure

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=================================================="
echo "  Testing FlywayProvisioner"
echo "=================================================="
echo ""

# Check if services are running
echo "→ Checking if services are running..."
if ! docker ps | grep -q testing-postgres-ssh; then
    echo "❌ PostgreSQL container is not running!"
    echo "   Run './testing/up.sh' first to start the infrastructure"
    exit 1
fi

if ! docker ps | grep -q mock-artifactory; then
    echo "❌ Mock Artifactory container is not running!"
    echo "   Run './testing/up.sh' first to start the infrastructure"
    exit 1
fi

echo "✓ Services are running"
echo ""

# Test PostgreSQL connection
echo "→ Testing PostgreSQL connection..."
if docker exec testing-postgres-ssh-1 psql -U postgres -d testdb -c "SELECT 1;" >/dev/null 2>&1; then
    echo "✓ PostgreSQL is accessible"
else
    echo "❌ Cannot connect to PostgreSQL"
    exit 1
fi

echo ""
echo "→ Running FlywayProvisioner..."
echo ""

# Run FlywayProvisioner using jbang
jbang FlywayProvisioner.java \
    --artifactory-base-url=http://localhost:8080/migrations \
    --schemas=customer:1.0.0,inventory:1.0.0,orders:1.0.0 \
    --db-url=jdbc:postgresql://localhost:5432/testdb \
    --db-user=postgres \
    --db-password=testpass \
    --baseline-location=./baseline-scripts \
    --cleanup=false \
    --fail-fast=false \
    -v

RESULT=$?

echo ""
echo "=================================================="
if [ $RESULT -eq 0 ]; then
    echo "✓ FlywayProvisioner completed successfully!"
    echo ""
    echo "→ Verifying schemas in database..."
    echo ""
    
    # Check if schemas were created
    docker exec testing-postgres-ssh-1 psql -U postgres -d testdb -c "\dn" | grep -E "customer|inventory|orders" || echo "Note: Check schema creation"
    
    echo ""
    echo "→ Checking Flyway schema history..."
    for schema in customer inventory orders; do
        echo ""
        echo "Schema: $schema"
        docker exec testing-postgres-ssh-1 psql -U postgres -d testdb -c "SELECT version, description, type, installed_on FROM $schema.flyway_schema_history ORDER BY installed_rank;" || echo "  (Schema history not found)"
    done
else
    echo "❌ FlywayProvisioner failed with exit code: $RESULT"
fi

echo "=================================================="
echo ""
echo "To reset the environment and test again:"
echo "  cd testing && ./reset.sh && ./up.sh"
echo ""
