FROM clojure:lein-2.8.1 AS base

RUN echo 'deb http://archive.debian.org/debian stretch main\n\
          deb http://archive.debian.org/debian-security stretch/updates main' > /etc/apt/sources.list

RUN apt update && \
    apt install -y build-essential cmake \
                   libboost-timer-dev libboost-program-options-dev \
                   libboost-filesystem-dev \
                   libpq-dev libgdal-dev postgresql-client libpq-dev \
                   gdal-bin python-gdal libgdal-java \
                   && \
    curl -sL https://deb.nodesource.com/setup_9.x | bash - && \
    apt-get install -y nodejs

WORKDIR /app

FROM base as build

COPY . /app

RUN cd client && npm install && npm run release
RUN lein uberjar
RUN scripts/build-binaries --release

FROM openjdk:8u242-jre-stretch

RUN echo 'deb http://archive.debian.org/debian stretch main\n\
          deb http://archive.debian.org/debian-security stretch/updates main' > /etc/apt/sources.list

# Install package dependencies and add precompiled binary
RUN apt-get update && \
    apt-get -y install postgresql-client gdal-bin python-gdal libgdal-java && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Add scripts
COPY --from=build /app/scripts/ /app/scripts/
ENV SCRIPTS_PATH /app/scripts/

# Add project compiled binaries
COPY --from=build /app/cpp/build-linux-x86_64/aggregate-population /app/bin/aggregate-population
COPY --from=build /app/cpp/build-linux-x86_64/walking-coverage /app/bin/walking-coverage
ENV BIN_PATH /app/bin/

# Add uberjar with app
COPY --from=build /app/target/uberjar/planwise-standalone.jar /app/lib/
ENV JAR_PATH /app/lib/planwise-standalone.jar

# Add app version file
COPY --from=build /app/resources/planwise/version /app/VERSION

# Expose JNI libs to app
ENV LD_LIBRARY_PATH=/usr/lib/jni

# Exposed port
ENV PORT 80
EXPOSE $PORT

# Data and tmp folders
ENV DATA_PATH /data/
ENV TMP_PATH /tmp/

CMD ["java", "-jar", "/app/lib/planwise-standalone.jar"]
