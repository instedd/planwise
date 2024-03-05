# Planwise Tools Docker Image

This is a Docker image to manage Planwise data installations, in particular country regions, friction layer and base demand raster layers of population.

NOTE: if running from a container exec shell (such as a Rancher console) try the `tools` command to get a properly setup shell.

Tools included:
- `psql`, `curl`, `vim`, `wget`, GDAL binary tools
- `gadm2geojson` for downloading gadm country regions and transforming them into GeoJSON
- `load-regions` to load the GeoJSON of the country/province regions into the database
- `update-region-previews` to generate low detail polygon of the regions for preview (uses mapshaper)
- `load-friction-raster` to generate cuts of the global friction raster for each region
- `update-source-sets` to manage system-wide raster source sets


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

* Download file raster file with the population count. The preferred source is 
  [Worldpop.org](https://hub.worldpop.org/project/categories?id=3). For our
  example, you can find a link to download the 2020 constrained UN adjusted
  population in Uruguay from
  [here](https://hub.worldpop.org/geodata/summary?id=50091). Look for the
  "Download entire data set" or "Browse individual files" button.
* You can use `wget`, `curl` or any other tool you may want.
* Move file to any folder below `$DATA_PATH`

Planwise needs the files to be encoded with Float32 data. You can check that using `gdalinfo`:

```
$ gdalinfo lbr_ppp_2020_UNadj.tif | grep 'Type='
Band 1 Block=4945x512 Type=Float32, ColorInterp=Gray
```

If you get a different Type (for example, `Type=Int32`), convert the file using `gdal_translate`:

```
$ gdalinfo landscan-global-2022.tif | grep 'Type='
Band 1 Block=512x512 Type=Int32, ColorInterp=Gray
$ gdal_translate -ot Float32 landscan-global-2022.tif landscan-global-2022-floats.tif
Input file size is 43200, 21600
0...10...20...30...40...50...60...70...80...90...100 - done.
$ gdalinfo landscan-global-2022-floats.tif | grep 'Type='
Band 1 Block=43200x1 Type=Float32, ColorInterp=Gray
```

When doing this, beware of the file size - uncompressed, the files can turn big:

```
$ ls -lh landscan-global-2022-floats.tif
-rw-r--r-- 1 node node  3.5G Mar  5 17:47 landscan-global-2022-floats.tif
```

You can check which compression algorithm the source data used:

```
$ gdalinfo landscan-global-2022.tif | grep COMPRESS
  COMPRESSION=DEFLATE
```

And apply the same compression algorithm during translation:

```
$ gdal_translate -ot Float32 -co "COMPRESS=DEFLATE" landscan-global-2022.tif landscan-global-2022-floats-deflate.tif
Input file size is 43200, 21600
0...10...20...30...40...50...60...70...80...90...100 - done.
$ ls -lh landscan-global-2022*tif
-rw-r--r--  1 node  node    90M Mar  5 17:51 landscan-global-2022-floats-deflate.tif
-rw-r--r--  1 node  node   3.5G Mar  5 17:47 landscan-global-2022-floats.tif
-rw-r--r--@ 1 node  node    86M Jul 18  2023 landscan-global-2022.tif
```

You can also try different compression algorithms and compare the sizes.

The compressed, Float32 file is the one you should add as a source set.

Then we need to create a record in the `source_set` table to point to the
downloaded raster file. You can use the provided `update-source-sets` tool for
this (or insert the database record manually).

```sh
update-source-sets add "Uruguay 2020 UN adjusted" ury_ppp_2020_UNadj_constrained.tif
```

The path to the raster file must be given relative to the `DATA_PATH`
environment variable.
