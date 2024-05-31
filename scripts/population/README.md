# Importing country population data

NOTE: these instructions may be outdated. The preferred way to import a country population data is to use the Planwise Tools Docker image. See scripts/Dockerfile and scripts/tools/README.

We use Uruguay as an example in the following sections.

## Import the region

The scripts at `scripts/geojson` (refer to `scripts/geojson/README.md`) will
leave the usable files under `data/geojson`. You need to import these into the
database by running `scripts/population/load-regions`. Eg.:

```sh
$ docker-compose run --rm app bash
/app$ scripts/population/load-regions URY Uruguay
```

### Geometry simplification

The script simplifies the regions using mapshaper by a distance between points.
We can adjust this parameter with the environment `INTERVAL_DISTANCE`.

For example to simplify with 120 meters distance

```sh
$ INTERVAL_DISTANCE=120 ./load-regions -f URY Uruguay
```

Here `-f` is used to force updating an existing region.

## Clip friction layer for new regions

The friction raster is clipped to the country-level regions for quicker access
(to avoid loading the full planet raster). Simply run:

```sh
$ docker-compose run --rm app bash
/app$ scripts/friction/load-friction-raster /data/friction/friction_surface_2015_v1.0.tif
```

NB. the actual full raster file may change. In any case, after running the
script, you will find the clipped friction raster files in
`data/friction/regions`

## Create a source record for the downloaded raster demand

* Download file `URY_ppp_v2b_2015.tif` from [worldpop.org.ok - Uruguay 100m
  Population](http://www.worldpop.org.uk/data/summary/?id=29) (click in "Browse
  Individual Files" and then "Switch to file view")
* Move file to any folder below `./data`

Then we need to create a record in the `source_set` table to point to the
downloaded raster file. Get access to the database and run the following SQL
snippet:

```sql
INSERT INTO source_set (name, type, unit, raster_file) VALUES ('Uruguay PPP v2b 2015', 'raster', 'people', 'URY_ppp_v2b_2015.tif');
```

The important bit of information is the last field. That should be a relative
path from the `DATA_PATH` directory to the downloaded raster file.
