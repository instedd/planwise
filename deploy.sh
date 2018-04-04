#!/bin/bash
set -euo pipefail

# To be invoked from circleci

if [ $# -lt 1 ]; then
  echo "Usage: $0 DOCKER_TAG"
	exit 1
fi

git describe --always > resources/planwise/version
docker-compose run --rm app npm install
docker-compose run --rm app lein uberjar
docker-compose run --rm app scripts/build-binaries --release

TAG=${1/\//_}

docker login -u ${DOCKER_USER} -p ${DOCKER_PASS} ${DOCKER_REGISTRY}
docker build -t planwise .
docker tag planwise ${DOCKER_REPOSITORY}:$TAG
docker push ${DOCKER_REPOSITORY}:$TAG
