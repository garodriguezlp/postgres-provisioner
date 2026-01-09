#!/bin/bash
# Script to rebuild mock-artifactory image with fresh migration artifacts
# NOTE: Artifacts are built into the mock-artifactory Docker image

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=================================================="
echo "  Rebuilding Mock Artifactory with Fresh Artifacts"
echo "=================================================="
echo ""
echo "Migration artifacts are built into the mock-artifactory image."
echo "This script will rebuild the image to include any changes"
echo "to the migration SQL files."
echo ""

echo "Stopping existing containers..."
docker-compose down

echo ""
echo "Rebuilding mock-artifactory image..."
docker-compose build --no-cache mock-artifactory

echo ""
echo "=================================================="
echo "✓ Image rebuilt successfully!"
echo "=================================================="
echo ""
echo "The following migration artifacts are included:"
echo "  - customer-1.5.0.zip"
echo "  - orders-2.3.1.zip"
echo "  - inventory-1.0.0.zip"
echo ""
echo "Start the environment with: ./up.sh"
