To run local:

(in project root)
```
$ cd scripts/geojson
$ npm install
$ node index.js ARG ../../data/geojson
```

To use docker:

(in project root)
```
$ docker build -t instedd/planwise:geojson-fetcher ./scripts/geojson
$ docker run --rm -v $(pwd)/data/geojson:/output instedd/planwise:geojson-fetcher ARG
```

TODO: out files
