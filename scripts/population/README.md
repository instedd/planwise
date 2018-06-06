# Importing country population data
## _(example country Uruguay)_

## Download files

### Regions file

See script at `/scripts/geojson` to download KMZ files from [GADM](https://gadm.org/) and convert them to geojson.

### Population file
* Download file `URY_ppp_v2b_2015.tif` from [worldpop.org.ok - Uruguay 100m Population](http://www.worldpop.org.uk/data/summary/?id=29) (click in "Browse Individual Files" and then "Switch to file view")
* Move file to folder: `./data`

## Run Script

Instead of using the country name as 3rd parameter. Now we use the country code: [ISO 3166-1 alpha-3](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-3) 
 
```diff
$ docker-compose run --rm app bash
/app$ lein import-population "ury_2015" "URY_ppp_v2b_2015.tif" "URY"
```

### Verbose option
```sh
/app$ lein import-population "ury_2015" "URY_ppp_v2b_2015.tif" "URY" --verbose
```

### Building C++ applications option
If you need to build the applications in the /cpp folder then add the following option:
```sh
/app$ lein import-population "ury_2015" "URY_ppp_v2b_2015.tif" "URY" --build-cpp
```

## _(example country Kenya)_

* Download file `KEN_popmap15_v2b.tif` from [worldpop.org.ok - Kenya 100m Population](http://www.worldpop.org.uk/data/summary/?id=29) in `./data`
* Run geojson script
* and then run:
```sh
$ lein import-population "ken_2015" "KEN_popmap15_v2b.tif" "KEN"
```
