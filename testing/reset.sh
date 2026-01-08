#!/bin/bash
# MinGW/Git Bash compatible script to reset the test environment (remove all data)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=================================================="
echo "  Resetting PostgreSQL Provisioner Test Environment"
echo "=================================================="
echo ""
echo "⚠️  WARNING: This will remove ALL data and volumes!"
echo ""
read -p "Are you sure you want to continue? (y/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Reset cancelled."
    exit 0
fi

echo ""
echo "Stopping and removing containers..."
docker-compose down

echo ""
echo "Removing volumes..."
docker-compose down -v

echo ""
echo "Removing orphaned containers..."
docker-compose down --remove-orphans

echo ""
echo "Cleaning up Docker images (postgres-provisioner-test)..."
docker rmi postgres-provisioner-test:latest 2>/dev/null || echo "Image already removed or doesn't exist"

echo ""
echo "✓ Environment reset successfully"
echo ""
echo "Run './up.sh' to start fresh environment."
