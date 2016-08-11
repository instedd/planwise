#!/bin/sh
${0%/*}/build-guisso-env.sh
docker-compose -f "${0%/*}/docker-compose.yml" up -d resmapdb
sleep 10
# setup database
docker-compose -f "${0%/*}/docker-compose.yml" run --rm resmapweb rake db:setup
# disable telemetry
docker-compose -f "${0%/*}/docker-compose.yml" run --rm resmapweb bash -c 'echo "InsteddTelemetry::Setting.set_all({disable_upload: true, dismissed: true, installation_info_synced: false})" | rails console'
docker-compose -f "${0%/*}/docker-compose.yml" up -d
