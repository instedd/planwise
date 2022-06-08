#!/bin/sh

export PATH=/tools:$PATH

echo "This is the Planwise Tools Docker image"
echo "You can find several helper scripts and tools in the /tools directory to manage data from a Planwise installation."
echo "Check the /tools/README.md file for more information (you can use the readme command)"
echo

if [ -z "$DATA_PATH" ]; then
    if [ -d /data ]; then
        echo The environment variable DATA_PATH is not defined. Using the default /data
        export DATA_PATH=/data
    else
        echo The environment variable DATA_PATH is not defined and there is no default /data directory. Some tools may not work. Did you mount the image properly?
    fi
else
    echo Using DATA_PATH at ${DATA_PATH}
fi
echo

if [ -z "$POSTGRES_HOST" ] || [ -z "$POSTGRES_PORT" ] || [ -z "$POSTGRES_USER" ] || [ -z "$POSTGRES_PASSWORD" ] || [ -z "$POSTGRES_DB" ]; then
    echo PostgreSQL configuration is missing or incomplete. Some tools will not work.
    echo Check the POSTGRES_HOST, POSTGRES_PORT, POSTGRES_USER, POSTGRES_DB and POSTGRES_PASSWORD environment variables.
    echo
else
    export PGDATABASE=$POSTGRES_DB
    export PGHOST=$POSTGRES_HOST
    export PGPORT=$POSTGRES_PORT
    export PGUSER=$POSTGRES_USER
fi

if [ -d "$DATA_PATH" ]; then
    cd $DATA_PATH
fi

exec "$@"
