#!/bin/bash
# MinGW/Git Bash compatible script to start the test environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/../docker" && pwd)"
cd "$DOCKER_DIR"

echo "=================================================="
echo "  Starting PostgreSQL Provisioner Test Environment"
echo "=================================================="
echo ""

# Build and start containers
echo "Building Docker images..."
docker-compose build

echo ""
echo "Starting services..."
docker-compose up -d

echo ""
echo "Waiting for services to be healthy..."
sleep 5

# Wait for PostgreSQL
echo -n "Waiting for PostgreSQL to be ready"
for i in {1..30}; do
    if docker-compose exec -T postgres-ssh pg_isready -U postgres >/dev/null 2>&1; then
        echo " ✓"
        break
    fi
    echo -n "."
    sleep 1
done

# Wait for Nginx
echo -n "Waiting for Mock Artifactory to be ready"
for i in {1..10}; do
    if curl -s http://localhost:8080/ >/dev/null 2>&1; then
        echo " ✓"
        break
    fi
    echo -n "."
    sleep 1
done

echo ""
echo "=================================================="
echo "  Environment Status"
echo "=================================================="
docker-compose ps

echo ""
echo "=================================================="
echo "  Service Endpoints"
echo "=================================================="
echo "PostgreSQL:        localhost:5432"
echo "  - Database:      testdb"
echo "  - User:          postgres"
echo "  - Password:      testpass"
echo ""
echo "SSH Access:        localhost:2222"
echo "  - User:          testuser"
echo "  - Password:      testpass"
echo ""
echo "Mock Artifactory:  http://localhost:8080/migrations"
echo ""
echo "=================================================="
echo "  Quick Test Commands"
echo "=================================================="
echo "# Test PostgreSQL connection:"
echo "psql -h localhost -U postgres -d testdb"
echo ""
echo "# Test SSH connection:"
echo "ssh -p 2222 testuser@localhost"
echo ""
echo "# View logs:"
echo "docker-compose logs -f"
echo ""
echo "=================================================="
