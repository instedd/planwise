#!/bin/bash
set -euo pipefail

cd $(dirname $0)/..

# To be invoked from circleci

if [ $# -lt 1 ]; then
  echo "Usage: $0 DOCKER_TAG"
	exit 1
fi

TAG=${1/\//_}

TOOLS_TAG=instedd/planwise-tools:$TAG

docker login -u ${DOCKER_USER} -p ${DOCKER_PASS} ${DOCKER_REGISTRY}
docker build -t $TOOLS_TAG .
docker push $TOOLS_TAG
