#!/bin/bash
set -e

# Start SSH daemon in background
echo "Starting SSH daemon..."
/usr/sbin/sshd -D &

# Execute original postgres entrypoint
echo "Starting PostgreSQL..."
exec docker-entrypoint.sh "$@"
