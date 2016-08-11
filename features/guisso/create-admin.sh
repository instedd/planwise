#!/bin/sh
CMD="User.new(email: \"$1\", role: \"admin\", password: \"$2\", password_confirmation: \"$2\").tap { |u| u.skip_confirmation!; u.save! }"
docker-compose -f "${0%/*}/docker-compose.yml" run -e CMD="$CMD" --rm guissoweb bash -c 'echo $CMD | rails console'
