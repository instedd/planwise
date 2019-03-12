FROM clojure:lein-2.8.1

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

