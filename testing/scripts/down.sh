#!/bin/bash
# MinGW/Git Bash compatible script to stop the test environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/../docker" && pwd)"
cd "$DOCKER_DIR"

echo "=================================================="
echo "  Stopping PostgreSQL Provisioner Test Environment"
echo "=================================================="
echo ""

echo "Stopping services..."
docker-compose down

echo ""
echo "✓ Environment stopped successfully"
echo ""
echo "Note: Docker volumes are preserved. Use './scripts/reset.sh' to remove all data."
