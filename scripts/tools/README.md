# Planwise Tools Docker Image

This is a Docker image to manage Planwise data installations, in particular country regions, friction layer and base demand raster layers of population.

Tools included:
- psql, curl, vim, wget
- gadm2geojson for downloading gadm country regions and transforming them into GeoJSON
- load-regions to load the GeoJSON of the country/province regions into the database
- update-region-previews to generate low detail polygon of the regions for preview (uses mapshaper)
- load-friction-raster to generate cuts of the global friction raster for each region


## Importing country population data

We use Uruguay as an example in the following sections.

### Import the country/provinces boundaries

1. Download the boundaries from gadm by using the `gadm2geojson` utility. Eg.
  ```sh
  gadm2geojson URY
  ```
  This will download KMZ files with the region boundaries and convert them to
  GeoJSON, leaving the resulting files in `$DATA_PATH/geojson`

2. Import the country boundaries into the database
  ```sh
  load-regions URY Uruguay
  ```

3. Generate reduced preview polygons for the imported regions
  ```sh
  update-regions-previews gen
  ```

### Clip friction layer for new regions

The friction raster needs to be clipped to the country-level regions for quicker
access (to avoid loading the full planet raster). Run:

```sh
load-friction-raster $DATA_PATH/friction/friction_surface_2015_v1.0.tif
```

This will only clip for new regions. If you need to regenerate all regions, run
the script with the `-f` option.

If you don't have the global friction raster downloaded yet, it can be obtained
from [The Malaria Atlas Project](https://malariaatlas.org/research-project/accessibility_to_cities/) 
([Direct download link](https://malariaatlas.org/geoserver/ows?service=CSW&version=2.0.1&request=DirectDownload&ResourceId=Explorer:2015_friction_surface_v1_Decompressed))

NB. the actual full raster file may change. In any case, after running the
script, you will find the clipped friction raster files in
`$DATA_PATH/friction/regions`

### Download a demand raster from Worldpop and create a source record for it

Planwise uses raster files with population data, aka. PPP or population per
pixel. Other data sets (such as population density rasters) need to be
transformed first.

* Download file `URY_ppp_v2b_2015.tif` from [worldpop.org.ok - Uruguay 100m
  Population](http://www.worldpop.org.uk/data/summary/?id=29) (click in "Browse
  Individual Files" and then "Switch to file view")
* Move file to any folder below `$DATA_PATH`

Then we need to create a record in the `source_set` table to point to the
downloaded raster file. Get access to the database and run the following SQL
snippet:

```sql
INSERT INTO source_set (name, type, unit, raster_file) VALUES ('Uruguay PPP v2b 2015', 'raster', 'people', 'URY_ppp_v2b_2015.tif');
```

The important bit of information is the last field. That should be a relative
path from the `DATA_PATH` directory to the downloaded raster file.
