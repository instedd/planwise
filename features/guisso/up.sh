#!/bin/sh
docker-compose -f "${0%/*}/docker-compose.yml" up -d db
sleep 10
# setup database
docker-compose -f "${0%/*}/docker-compose.yml" run --rm web rake db:setup
# disable telemetry
docker-compose -f "${0%/*}/docker-compose.yml" run --rm web bash -c 'echo "InsteddTelemetry::Setting.set_all({disable_upload: true, dismissed: true, installation_info_synced: false})" | rails console'
docker-compose -f "${0%/*}/docker-compose.yml" up -d
