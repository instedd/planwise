FROM java:8u91-jre

# Install osm2pgrouting dependencies and add precompiled binary
RUN apt-get update && apt-get -y install libboost-program-options-dev libpq-dev && apt-get clean && rm -rf /var/lib/apt/lists/*
ADD docker/osm2pgrouting /usr/local/bin/osm2pgrouting

# Add scripts folder
ADD scripts /app/scripts

# Add uberjar with app
ADD ./target/uberjar/planwise-0.1.0-SNAPSHOT-standalone.jar /app/srv/

CMD ["java", "-jar", "/app/srv/planwise-0.1.0-SNAPSHOT-standalone.jar"]
