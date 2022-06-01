#!/bin/bash
set -euo pipefail

cd $(dirname $0)

# To be invoked from circleci

if [ $# -lt 1 ]; then
  echo "Usage: $0 DOCKER_TAG"
	exit 1
fi

TAG=${1/\//_}

MAPCACHE_TAG=instedd/planwise-mapserver:mapcache-$TAG
MAPSERVER_TAG=instedd/planwise-mapserver:mapserver-$TAG

docker login -u ${DOCKER_USER} -p ${DOCKER_PASS} ${DOCKER_REGISTRY}
docker build -t $MAPSERVER_TAG -f Dockerfile.mapserver .
docker build -t $MAPCACHE_TAG -f Dockerfile.mapcache .
docker push $MAPSERVER_TAG
docker push $MAPCACHE_TAG
