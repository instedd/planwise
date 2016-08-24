docker-compose --file "${0%/*}/docker-compose.yml" pull
docker-compose --file "${0%/*}/proxy/docker-compose.yml" pull
docker-compose --file "${0%/*}/planwise/docker-compose.yml" pull
docker-compose --file "${0%/*}/resourcemap/docker-compose.yml" pull
docker-compose --file "${0%/*}/guisso/docker-compose.yml" pull
docker-compose --file "${0%/*}/selenium/docker-compose.yml" pull
docker-compose --file "${0%/*}/smtp/docker-compose.yml" pull
