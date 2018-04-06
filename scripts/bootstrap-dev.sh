#!/bin/sh

set -e

if ! which osm2pgrouting; then
    echo Missing osm2pgrouting in PATH
    exit 1
fi

cd $(dirname $0)/..

scripts/build-binaries
scripts/migrate
scripts/import-osm $(which osm2pgrouting) kenya
scripts/load-regions kenya
