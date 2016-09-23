#! /bin/bash
#
# Tests calculate-demand for every region for a given dataset, using every
# facility polygon on a max threshold

set -euo pipefail

export PGPASSWORD=$POSTGRES_PASSWORD;

BIN_PATH=${BIN_PATH:-cpp}
SCRIPT_PATH=${SCRIPT_PATH:-scripts}
DATA_PATH=${DATA_PATH:-data}

country=${1:-kenya}
dataset=$2

function query {
  psql -d $POSTGRES_DB -U $POSTGRES_USER -h $POSTGRES_HOST -t -A -c "$1";
}

region_ids=$(query "SELECT id FROM regions
                    WHERE country='${country}'
                    AND admin_level > 2
                    ORDER BY id;")

for region_id in $region_ids; do

  facility_polygon_ids=$(query "SELECT fp.id
                                FROM facilities_polygons fp
                                INNER JOIN facilities f ON f.id = fp.facility_id
                                WHERE fp.threshold = 10800
                                AND f.processing_status = 'ok'
                                AND f.dataset_id=${dataset}
                                AND ST_Contains((SELECT the_geom FROM regions WHERE id = ${region_id} LIMIT 1), f.the_geom)")

  cmd="${BIN_PATH}/calculate-demand tmp.tif 2000 ${DATA_PATH}/populations/data/${region_id}.tif"

  for fp_id in $facility_polygon_ids; do
    fp_file="${DATA_PATH}/isochrones/${region_id}/${fp_id}.tif"
    if [[ -e $fp_file ]]; then
      cmd+=" ${fp_file} 1000"
    fi;
  done;

  rm -f tmp.tif
  echo "Testing region ${region_id}"
  echo " Running ${cmd}"
  $cmd
  rm -f tmp.tif

done
