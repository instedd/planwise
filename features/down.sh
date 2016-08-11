docker-compose -f "${0%/*}/docker-compose.yml" down -v
${0%/*}/resourcemap/down.sh
${0%/*}/guisso/down.sh
${0%/*}/selenium/down.sh
${0%/*}/smtp/down.sh
${0%/*}/network/down.sh
