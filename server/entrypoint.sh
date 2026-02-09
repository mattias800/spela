#!/bin/sh
set -e

if [ "$SPELA_SEED" = "true" ]; then
    echo "Seeding database..."
    ./spela-seed
fi

exec ./spela-server
