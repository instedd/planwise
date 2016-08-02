FROM java:8u91-jre

# Install package dependencies and add precompiled binary
RUN apt-get update && apt-get -y install postgresql-client libboost-program-options-dev libpq-dev && apt-get clean && rm -rf /var/lib/apt/lists/*
ADD docker/osm2pgrouting /usr/local/bin/osm2pgrouting

# Add scripts
ADD scripts/import-osm /app/scripts/import-osm
ADD scripts/mapconfig.xml /app/scripts/mapconfig.xml
ADD scripts/import-sites /app/scripts/import-sites
ADD scripts/migrate /app/scripts/migrate
ADD scripts/preprocess-isochrones /app/scripts/preprocess-isochrones
ADD scripts/load-regions /app/scripts/load-regions

# Add uberjar with app
ADD ./target/uberjar/planwise-0.5.0-SNAPSHOT-standalone.jar /app/lib/
ENV JAR_PATH /app/lib/planwise-0.5.0-SNAPSHOT-standalone.jar

# Exposed port
ENV PORT 80
EXPOSE $PORT

CMD ["java", "-jar", "/app/lib/planwise-0.5.0-SNAPSHOT-standalone.jar"]
