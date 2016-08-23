${0%/*}/network/up.sh
docker-compose --file "${0%/*}/smtp/docker-compose.yml" up -d
docker-compose --file "${0%/*}/selenium/docker-compose.yml" up -d
docker-compose --file "${0%/*}/guisso/docker-compose.yml" up -d
docker-compose --file "${0%/*}/resourcemap/docker-compose.yml" up -d
docker-compose --file "${0%/*}/planwise/docker-compose.yml" up -d
docker-compose --file "${0%/*}/proxy/docker-compose.yml" up -d
docker-compose -f "${0%/*}/docker-compose.yml" up
