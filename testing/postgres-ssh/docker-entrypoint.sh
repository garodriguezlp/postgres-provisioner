#!/bin/bash
set -e

# Start SSH daemon in background
echo "Starting SSH daemon..."
/usr/sbin/sshd -D &

# Check if this is the first run
FIRST_RUN_MARKER="/var/lib/postgresql/data/.first_run_complete"

if [ ! -f "$FIRST_RUN_MARKER" ]; then
    # First run - execute original postgres entrypoint in background to let it initialize
    echo "First run - Starting PostgreSQL initialization..."
    docker-entrypoint.sh "$@" &
    POSTGRES_PID=$!

    # Wait for PostgreSQL to initialize
    echo "Waiting for PostgreSQL to be ready..."
    sleep 10

    # Now modify the config to "break" remote connections
    # This simulates a misconfigured PostgreSQL that our SSH configurator will fix
    PG_CONF="/var/lib/postgresql/data/postgresql.conf"
    PG_HBA="/var/lib/postgresql/data/pg_hba.conf"

    if [ -f "$PG_CONF" ]; then
        echo "Skipping restrictive PostgreSQL configuration (commented out for testing)"
        
        # Set listen_addresses to localhost only (wrong config)
        # sed -i "s/^#*listen_addresses.*=.*/listen_addresses = 'localhost'/" "$PG_CONF"
        
        # Set shared_buffers to a low value (wrong config)
        # sed -i "s/^#*shared_buffers.*=.*/shared_buffers = 128MB/" "$PG_CONF"
        
        echo "PostgreSQL will use default configuration (accessible from outside)"
        echo "  - listen_addresses = '*' (default)"
        echo "  - shared_buffers = default"
        
        # Mark first run as complete
        touch "$FIRST_RUN_MARKER"
        
        # No restart needed - using default config
        # echo "Restarting PostgreSQL with restrictive config..."
        # kill -TERM $POSTGRES_PID
        # wait $POSTGRES_PID
        
        # Wait for PostgreSQL to finish starting
        wait $POSTGRES_PID
    else
        # If config doesn't exist yet, just wait for postgres
        wait $POSTGRES_PID
    fi
else
    # Subsequent runs - just start PostgreSQL normally
    echo "Starting PostgreSQL with existing configuration..."
    exec docker-entrypoint.sh "$@"
fi
