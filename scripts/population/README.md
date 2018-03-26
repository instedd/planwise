# Importing country population data
## _(example country Uruguay)_

## Download files

### Regions file

* Download former MapZen boundary information and store it as `./data/uruguay_geojson.tgz`

```sh
$ curl -L https://github.com/instedd/planwise/files/1803300/uruguay_geojson.tar.gz -o ./data/uruguay_geojson.tgz
```

### Population file
* Download file `URY_ppp_v2b_2015.tif` from [worldpop.org.ok - Uruguay 100m Population](http://www.worldpop.org.uk/data/summary/?id=29) (click in "Browse Individual Files" and then "Switch to file view")
* Move file to folder: `./data`

## Run Script
```sh
$ docker-compose exec app bash
/app$ lein import-population "ury_2015" "URY_ppp_v2b_2015.tif" "uruguay"
```

### Verbose option
```sh
/app$ lein import-population "ury_2015" "URY_ppp_v2b_2015.tif" "uruguay" --verbose
```

### Building C++ applications option
If you need to build the applications in the /cpp folder then add the following option:
```sh
/app$ lein import-population "ury_2015" "URY_ppp_v2b_2015.tif" "uruguay" --build-cpp
```

## _(example country Kenya)_

* Download file `KEN_popmap15_v2b.tif` from [worldpop.org.ok - Kenya 100m Population](http://www.worldpop.org.uk/data/summary/?id=29) in `./data`

```sh
$ curl -L https://github.com/instedd/planwise/files/1803299/kenya_geojson.tar.gz -o ./data/kenya_geojson.tgz

$ lein import-population "ken_2015" "KEN_popmap15_v2b.tif" "kenya"
```
