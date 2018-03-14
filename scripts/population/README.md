# Importing country population data
## _(example country Uruguay)_

## Download files

### Regions file
* Download [uruguay_geojson.tar.gz](https://github.com/instedd/planwise/files/1803300/uruguay_geojson.tar.gz)
* Rename uruguay_geojson.tar.gz to uruguay_geojson.tgz
* Move file to folder: /app/data
### Population file
* Download file URY_ppp_v2b_2015.tif from [www.worldpop.org.uk](http://www.worldpop.org.uk/)
* Move file to folder: /app/data

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
