# Kml to GeoJson script

## Running the script

### To run local:
(in project root)
```
$ cd scripts/geojson
$ npm install
$ node index.js ARG ../../data/geojson
```

### To use docker:
(in project root)
```
$ docker build -t instedd/planwise:geojson-fetcher ./scripts/geojson
$ docker run --rm -v $(pwd)/data/geojson:/output instedd/planwise:geojson-fetcher ARG
```

### Output files:
The script generates two geojson files:

  * _`<country_code>`_`_adm0.geojson`
  * _`<country_code>`_`_adm1.geojson`

and they will be placed at the output folder inside another folder with the country code as name.

__For example:__

If the output folder is `../../data/geojson` then the files will be:

  * `../../data/geojson/ARG/ARG_adm0.geojson`
  * `../../data/geojson/ARG/ARG_adm1.geojson`

## Example country code
[ISO 3166-1 alpha-3](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-3)

  Country | Code
  ---| :---:
  Kenya | KEN
  Pakistan | PAK
  Philippines | PHL
  Nepal | NPL
  Burkina Faso | BFA
