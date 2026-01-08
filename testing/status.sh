#!/bin/bash
# MinGW/Git Bash compatible script to show environment status

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=================================================="
echo "  PostgreSQL Provisioner Test Environment Status"
echo "=================================================="
echo ""

echo "Docker Compose Services:"
docker-compose ps

echo ""
echo "=================================================="
echo "  Health Checks"
echo "=================================================="

# Check PostgreSQL
echo -n "PostgreSQL:        "
if docker-compose exec -T postgres-ssh pg_isready -U postgres >/dev/null 2>&1; then
    echo "✓ Healthy"
else
    echo "✗ Not responding"
fi

# Check SSH
echo -n "SSH:               "
if docker-compose exec -T postgres-ssh pgrep sshd >/dev/null 2>&1; then
    echo "✓ Running"
else
    echo "✗ Not running"
fi

# Check Nginx
echo -n "Mock Artifactory:  "
if curl -s http://localhost:8080/ >/dev/null 2>&1; then
    echo "✓ Responding"
else
    echo "✗ Not responding"
fi

echo ""
echo "=================================================="
echo "  Disk Usage"
echo "=================================================="
docker system df -v | grep -E "VOLUME NAME|postgres-data" || echo "No volumes found"

echo ""
