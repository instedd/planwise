FROM node:9.4-alpine

ADD gadm2geojson.js /src/
ADD package*.json /src/

WORKDIR /src
RUN npm install

ENTRYPOINT ["node", "gadm2geojson.js"]
