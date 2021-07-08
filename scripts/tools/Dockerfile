FROM node:stretch-slim

# Install some basic tools
RUN \
  apt-get update \
  && apt-get install -y curl vim \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# Install mapshaper
RUN npm install -g mapshaper

WORKDIR /tmp

# Install babashka
RUN \
  curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install && \
  chmod +x ./install && \
  ./install

# Preload required pods
RUN \
  bb -e "(require '[babashka.pods :as pods]) (pods/load-pod 'org.babashka/postgresql \"0.0.8\")"

# Add sources for tools
ADD update-region-previews /tools/
RUN chmod +x /tools/update-region-previews

WORKDIR /tools
