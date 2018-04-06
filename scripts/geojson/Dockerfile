FROM node:9.4-alpine

ADD index.js /src/
ADD package*.json /src/

WORKDIR /src
RUN npm install

ENTRYPOINT ["node", "index.js"]
