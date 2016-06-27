#!/bin/bash
set -euo pipefail

curl -XGET https://s3.amazonaws.com/planwise/data/kenya-20160627.osm.gz | gunzip > kenya.osm
osm2pgrouting -f kenya.osm -c ./mapconfig.xml -d $POSTGRES_DB -U $POSTGRES_USER -h $POSTGRES_HOST
