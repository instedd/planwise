#!/bin/sh
GUISSO_ENV_FILE="${0%/*}/guisso.env"
docker-compose -f "${0%/*}/../guisso/docker-compose.yml" run --rm guissoweb rake apps:create[resmap,resmapweb:5080,trusted] > $GUISSO_ENV_FILE
sed -i -e 's/^client_id:\ /GUISSO_CLIENT_ID=/g' $GUISSO_ENV_FILE
sed -i -e 's/^client_secret:\ /GUISSO_CLIENT_SECRET=/g' $GUISSO_ENV_FILE
rm "$GUISSO_ENV_FILE-e"
