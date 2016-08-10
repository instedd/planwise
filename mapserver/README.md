# Planwise mapserver

This folder contains docker files for building and running a mapserver setup for Planwise. Though the staging instance in http://planwise-maps-stg.instedd.org can be used for development, it is suggested to run them locally in order to be able to visualise calculated unsatisfied demand rasters.

## Run mapserver containers

Use the supplied `docker-compose` file:
```
docker-compose -f mapserver/docker-compose.yml up
```

This will start a mapserver container and a mapcache container, mapped to ports 5001 and 5002 respectively. Both will use the config files from the `mapserver` folder and the data from the `data` folder, mounted as volumes.

### Structure

Mapcache will receive all tile requests from browser clients in `WMS`. Though mapcache can also accept requests in the more used `TMS` or `gmaps` formats, it only supports _dimensions_ in WMS. The service is configured to accept a `DATAFILE` dimension, which is used to determine the source raster so clients can request a specific unsatisfied demand raster, falling back to `KEN_popmap15_v2b` that contains the original demographics.

Mapcache will forward all requests to Mapserver in WMS format as well, and cache the requested tiles locally on disk at `/tmp`. Mapserver is configured to respond both in tile mode and `WMS`, and accepts the `DATAFILE` query param to change the source raster.

### Endpoints

* To query mapcache in WMS format the root URL should be `http://localhost:5002/mapcache`, specifying `kenya` as `LAYER`, and optionally setting `DATAFILE`. For example:
```
http://localhost:5002/mapcache?&SERVICE=WMS&REQUEST=GetMap&VERSION=1.1.1&LAYERS=kenya&STYLES=&FORMAT=image%2Fjpeg&TRANSPARENT=true&HEIGHT=256&WIDTH=256&DATAFILE=KEN_popmap15_v2b&SRS=EPSG%3A3857&BBOX=3913575.848201024,-313086.067856082,4070118.882129065,-156543.03392804056
```

* To query mapcache in gmaps format, which does not support the `DATAFILE` parameter:
```
http://localhost:5002/mapcache/gmaps/kenya@GoogleMapsCompatible/{z}/{x}/{y}.png
```

*  To bypass mapcache and query mapserver in tile mode:
```
http://localhost:5001/mapserv?map=/etc/mapserver/kenya.map&mode=tile&layers=KenyaPopulation&tile={x}+{y}+{z}
```

### Data container

To run on docker cloud, there is a third data container `kenya-data`, which simply exposes a data volume with the population data TIFF file with already generated overviews, and is linked from the mapserver container.

## Local install

Mapserver can also be installed locally on OSX, via `brew install mapserver`. If that doesn't work, try using the following homebrew tap: `brew tap osgeo/osge4mac`.

## Main data

Download Kenya geotiff file `KEN_popmap15_v2b.tif` from [worldpop.org.uk](http://www.worldpop.org.uk/data/files/index.php?dataset=KEN-POP&action=group), and place it in the `data` folder.

### Optimisations

* To add overviews to the tiff file, which yields a noticeable performance improvement:
```
gdaladdo KEN_popmap15_v2b.tif -r average 2 4 8 16 32 64
```

* To create a tiled tif, which does not show much improvement:
```
gdal_translate -co TILED=YES KEN_popmap15_v2b.tif KEN_popmap15_v2b.tiled.tif
```

* To create a tile index from the geotiff, use the custom python script:
```
python partition.py
```

### Preview

The generation of a map may be tested using the `mapserv` utility [directly](http://mapserver.org/ar/cgi/mapserv.html):

`$ mapserv -nh "QUERY_STRING=map=kenya.map&mode=map" > test.png`

This can be run both from inside the docker container or the locally installed mapserver. Just set the full path to `kenya.map` on the query string if needed, and ensure the data file is reachable.

## References

- Raster layers configuration and preprocessing
  http://mapserver.org/ar/input/raster.html
- Mapserver general documentation
  http://mapserver.org/ar/documentation.html
- Mapcache config specification
  http://mapserver.org/mapcache/config.html
