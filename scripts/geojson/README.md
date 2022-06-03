# Kml to GeoJson script

NOTE: this tool is available in the Planwise Tools Docker image. See scripts/Dockerfile and scripts/tools/README

## Running the script

### To run local:
(in project root)
```
$ cd scripts/geojson
$ npm install
$ ./gadm2geojson ARG ../../data/geojson
```

__Note:__ If you prefer to run the script directly with Node.js, you could replace the last line with:
```
$ node gadm2geojson.js ARG ../../data/geojson
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

  Country      | Code
  -------------| :---:
  Kenya        | KEN
  Pakistan     | PAK
  Philippines  | PHL
  Nepal        | NPL
  Burkina Faso | BFA
