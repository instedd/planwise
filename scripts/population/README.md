
## Importing country population data

### Example country Uruguay
```sh
$ docker-compose exec app bash
$ cd scripts/population
/app/scripts/population$ lein run "ury_2015" "URY_ppp_v2b_2015.tif" "uruguay"
```

### Building C++ applications
If you need to build the applications in the /cpp folder then add the following option:
```sh
/app/scripts/population$ lein run "ury_2015" "URY_ppp_v2b_2015.tif" "uruguay" --build-cpp
```
