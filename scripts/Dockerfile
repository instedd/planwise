# Dockerfile to create the instedd/planwise-tools image

FROM node:bullseye-slim

WORKDIR /tmp

# Install some basic tools
RUN \
  apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install -y curl wget less vim procps postgresql-client gdal-bin \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# Install mapshaper
RUN npm install -g mapshaper

# Install glow (for reading Markdown)
RUN \
  wget https://github.com/charmbracelet/glow/releases/download/v1.4.1/glow_1.4.1_linux_amd64.deb && \
  dpkg -i glow*.deb

# Install babashka
RUN \
  curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install && \
  chmod +x ./install && \
  ./install --version 0.8.2

# Preload required pods
RUN \
  bb -e "(require '[babashka.pods :as pods]) (pods/load-pod 'org.babashka/postgresql \"0.1.0\")"

# Add sources for tools
ADD tools/update-region-previews /tools/
RUN chmod +x /tools/update-region-previews
ADD tools/update-source-sets /tools/
RUN chmod +x /tools/update-source-sets

# Add geojson tool
ADD geojson/ /tools/geojson/
RUN \
  cd /tools/geojson && npm install && npm install -g

# Add load-regions script
ADD population/load-regions /tools/
RUN chmod +x /tools/load-regions

# Add friction layer tools
ADD friction/load-friction-raster /tools/
RUN chmod +x /tools/load-friction-raster

# Add docker-entrypoint.sh overriding the base default
ADD tools/docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ADD tools/README.md /tools/
ADD tools/readme /usr/local/bin/
RUN chmod +x /usr/local/bin/readme
ADD tools/tools /usr/local/bin/
RUN chmod +x /usr/local/bin/tools

WORKDIR /tools

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/bin/bash"]
