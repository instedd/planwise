${0%/*}/network/up.sh
${0%/*}/smtp/up.sh
${0%/*}/selenium/up.sh
${0%/*}/guisso/up.sh
${0%/*}/resourcemap/up.sh
${0%/*}/planwise/up.sh
${0%/*}/proxy/up.sh
docker-compose -f "${0%/*}/docker-compose.yml" run --rm --no-deps ruby bash -c 'cd /features && bundle install'
docker-compose -f "${0%/*}/docker-compose.yml" up
