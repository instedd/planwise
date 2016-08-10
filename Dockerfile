FROM java:8u91-jre

# Install package dependencies and add precompiled binary
RUN apt-get update && apt-get -y install postgresql-client libboost-program-options-dev libpq-dev gdal-bin && apt-get clean && rm -rf /var/lib/apt/lists/*
ADD docker/osm2pgrouting /usr/local/bin/osm2pgrouting

# Add scripts
ADD scripts/* /app/scripts/
ENV SCRIPTS_PATH /app/scripts/

# Add project compiled binaries
ADD cpp/calculate-demand /app/bin/calculate-demand
ADD cpp/count-population /app/bin/count-population
ENV BIN_PATH /app/bin/

# Add uberjar with app
ADD ./target/uberjar/planwise-0.5.0-SNAPSHOT-standalone.jar /app/lib/
ENV JAR_PATH /app/lib/planwise-0.5.0-SNAPSHOT-standalone.jar

# Exposed port
ENV PORT 80
EXPOSE $PORT

# Data and tmp folders
ENV DATA_PATH /data/
ENV TMP_PATH /tmp/

CMD ["java", "-jar", "/app/lib/planwise-0.5.0-SNAPSHOT-standalone.jar"]
