#!/bin/bash
# MinGW/Git Bash compatible script to generate test artifact ZIP files

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="$SCRIPT_DIR/test-artifacts"

echo "=================================================="
echo "  Generating Test Migration Artifacts"
echo "=================================================="
echo ""

# Create output directory structure
mkdir -p "$MIGRATIONS_DIR"/{customer,orders,inventory}

# Function to create ZIP from migration directory
create_zip() {
    local schema=$1
    local version=$2
    local source_dir="$MIGRATIONS_DIR/${schema}-migrations"
    local output_file="$MIGRATIONS_DIR/${schema}/${schema}-${version}.zip"
    
    if [ ! -d "$source_dir" ]; then
        echo "✗ Error: Source directory not found: $source_dir"
        return 1
    fi
    
    echo -n "Creating ${schema}-${version}.zip... "
    
    # Navigate to source directory and create zip
    cd "$source_dir"
    zip -q -r "$output_file" . -i "*.sql"
    cd "$SCRIPT_DIR"
    
    # Get file size
    local size=$(stat -f%z "$output_file" 2>/dev/null || stat -c%s "$output_file" 2>/dev/null || echo "unknown")
    
    if [ "$size" != "unknown" ]; then
        size=$(echo "scale=2; $size/1024" | bc)
        echo "✓ ($size KB)"
    else
        echo "✓"
    fi
}

# Generate artifacts for each schema
echo "Generating customer artifacts..."
create_zip "customer" "1.5.0"

echo ""
echo "Generating orders artifacts..."
create_zip "orders" "2.3.1"

echo ""
echo "Generating inventory artifacts..."
create_zip "inventory" "1.0.0"

echo ""
echo "=================================================="
echo "  Generated Artifacts"
echo "=================================================="
echo ""

# List all generated files
for schema in customer orders inventory; do
    echo "${schema}:"
    ls -lh "$MIGRATIONS_DIR/${schema}/"*.zip 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
done

echo ""
echo "=================================================="
echo "  Artifact Structure Verification"
echo "=================================================="
echo ""

# Verify each ZIP contains SQL files
for schema in customer orders inventory; do
    echo "${schema}:"
    for zipfile in "$MIGRATIONS_DIR/${schema}/"*.zip; do
        if [ -f "$zipfile" ]; then
            unzip -l "$zipfile" | grep -E "\.sql$" | awk '{print "  " $4}'
        fi
    done
    echo ""
done

echo "=================================================="
echo "✓ All test artifacts generated successfully!"
echo "=================================================="
echo ""
echo "Artifacts are ready at: $MIGRATIONS_DIR"
echo ""
echo "You can now start the test environment with './up.sh'"
