#!/bin/sh
docker-compose -f "${0%/*}/docker-compose.yml" start
