#!/bin/bash
set -eo pipefail

source <(curl -s https://raw.githubusercontent.com/manastech/ci-docker-builder/d3406587def914918666ef41c0637d6b739fdf7d/build.sh)

dockerSetup
echo $VERSION > VERSION
echo $VERSION > resources/planwise/version

if [[ -z "$DOCKER_TAG" ]]; then
  echo "Not building because DOCKER_TAG is undefined"
  exit 0
fi

dockerBuildAndPush
dockerBuildAndPush -d mapserver -s "-mapserver" -o "-f mapserver/Dockerfile.mapserver"
dockerBuildAndPush -d mapserver -s "-mapcache" -o "-f mapserver/Dockerfile.mapcache"
dockerBuildAndPush -d scripts -s "-tools"
