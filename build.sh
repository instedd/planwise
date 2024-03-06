#!/bin/bash
set -eo pipefail

source <(curl -s https://raw.githubusercontent.com/manastech/ci-docker-builder/d3406587def914918666ef41c0637d6b739fdf7d/build.sh)

dockerSetup
echo $VERSION > VERSION
echo $VERSION > resources/planwise/version

dockerBuildAndPush
# FIXME: build & push mapcache
# FIXME: build & push mapserver
# FIXME: build & push tools
