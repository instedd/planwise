#!/bin/bash

if [ $# -lt 1 ]; then
  tag=`git rev-parse --abbrev-ref HEAD`
else
  tag=$1
fi
echo Using $tag as the version tag. Will build and push:
echo - instedd/planwise-mapserver:mapserver-$tag
echo - instedd/planwise-mapserver:mapcache-$tag
echo 
read -p "Press Enter to continue, Ctrl+C to cancel: " dummy

docker build -t instedd/planwise-mapserver:mapserver-$tag -f Dockerfile.mapserver .
docker push instedd/planwise-mapserver:mapserver-$tag

docker build -t instedd/planwise-mapserver:mapcache-$tag -f Dockerfile.mapcache .
docker push instedd/planwise-mapserver:mapcache-$tag

