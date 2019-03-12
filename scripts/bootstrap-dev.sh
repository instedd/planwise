#!/bin/sh

set -e

cd $(dirname $0)/..

scripts/build-binaries
scripts/migrate
