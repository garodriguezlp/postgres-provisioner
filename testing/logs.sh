#!/bin/bash
# MinGW/Git Bash compatible script to show logs

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Default to follow mode
FOLLOW_FLAG="-f"

# Check if user passed --no-follow or -n
if [[ "$1" == "--no-follow" ]] || [[ "$1" == "-n" ]]; then
    FOLLOW_FLAG=""
    shift
fi

# If a service name is provided, show logs for that service only
if [ -n "$1" ]; then
    echo "Showing logs for service: $1"
    docker-compose logs $FOLLOW_FLAG "$1"
else
    echo "Showing logs for all services (Ctrl+C to exit)"
    docker-compose logs $FOLLOW_FLAG
fi
