# Planwise mapserver

This folder contains docker files for building and running a mapserver setup for Planwise. Though the staging instance in http://planwise-maps-stg.instedd.org can be used for development, the mapserver servers can also be run in local docker containers.

## Run mapserver containers

Use the supplied `docker-compose` file:
```
docker-compose -f mapserver/docker-compose.yml up
```

This will start a mapserver container and a mapcache container, mapped to ports 5001 and 5002 respectively. Both will use the data and config files from the `mapserver` folder, mounted as volumes.

Make sure to add the following entry to your `profiles.clj` so your local instance loads tiles from your docker containers and not from the cloud:
```clojure
:demo-tile-url "http://localhost:5002/mapcache/gmaps/kenya@GoogleMapsCompatible/{z}/{x}/{y}.png"
```

To query mapserver directly, instead of the mapcache server, change it to:
```clojure
:demo-tile-url "http://localhost:5001/mapserv?map=/etc/mapserver/kenya.map&mode=tile&layers=KenyaPopulation&tile={x}+{y}+{z}"
```

To query mapserver at the WMS service, manually set the following in the `tile-layer` properties:
```clojure
:url "http://localhost:5001/mapserv?map=/etc/mapserver/kenya.map"
:wms true
:layers "KenyaPopulation"
:format "image/png"
```


### Data container

To run on docker cloud, there is a third data container `kenya-data`, which simply exposes a data volume with the population data TIFF file with already generated overviews, and is linked from the mapserver container.

## Local install

Mapserver can also be installed locally on OSX, via `brew install mapserver`. If that doesn't work, try using the following homebrew tap: `brew tap osgeo/osge4mac`.

## Data

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

- Raster layers configuration and preprocessing.
  http://mapserver.org/ar/input/raster.html
- The mapserver documentation is good.
  http://mapserver.org/ar/documentation.html
