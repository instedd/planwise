#!/bin/sh
docker-compose -f "${0%/*}/docker-compose.yml" up -d guissodb
sleep 10
# setup database
docker-compose -f "${0%/*}/docker-compose.yml" run --rm guissoweb rake db:setup
# disable telemetry
docker-compose -f "${0%/*}/docker-compose.yml" run --rm guissoweb bash -c 'echo "InsteddTelemetry::Setting.set_all({disable_upload: true, dismissed: true, installation_info_synced: false})" | rails console'
# create default admin
${0%/*}/create-admin.sh admin@instedd.org admin123
docker-compose -f "${0%/*}/docker-compose.yml" up -d
