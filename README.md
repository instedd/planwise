# PlanWise

PlanWise applies algorithms and geospatial optimisation techniques to existing
data on population, road networks and health facilities, so health care planners
can better understand the unmet health needs of their constituents and better
locate future health facilities. Together, InSTEDD and Concern Worldwide are
developing PlanWise, to apply such algorithms to help governments visualize how
potential future facilities will improve access to healthcare in underserved
areas.

## Technology

PlanWise is a single page web application implemented in Clojure and
Clojurescript, backed with PostgreSQL database storing both relational and
spatial information, and GeoTiff raster files for demographics data.

### Tech Stack

Server side:

* [Clojure](http://clojure.org/) application, built on top of
  the [Duct](https://github.com/duct-framework/duct) framework, served using
  a [Jetty](http://www.eclipse.org/jetty/) webserver.
* [PostgreSQL](https://www.postgresql.org/) database
  with [PostGIS](http://postgis.net/) and [pgRouting](http://pgrouting.org/)
  extensions providing spatial and routing capabilities.
* [Mapserver](http://mapserver.org/) with
  a [Mapcache](http://mapserver.org/mapcache/index.html) caching façade for
  serving the demographics raster layers.
* [GDAL](http://gdal.org/) tool suite for manipulating raster and vector
  spatial data files.

Client side:

* [Clojurescript](http://clojurescript.org) application using
  the [re-frame](https://github.com/Day8/re-frame) framework, which is built on
  top of [reagent](https://github.com/reagent-project/reagent)
  and [React](https://facebook.github.io/react/).
* [Leaflet](http://leafletjs.com/) for displaying interactive maps.

Deployment:

* The production application is deployed as a set of [Docker](http://docker.io/)
  containers.

### Scalability

The webserver is multi-threaded and there is no lock contention on any
operation, so it can handle multiple concurrent requests. The PostgreSQL
database can also handle multiple concurrent connections. Pre-processing of
facilities is parallelized to make use of multiple cores, and can be easily
extended to multiple hosts.

Beyond that, there are three main dimensions for scaling PlanWise:

* Number of analyzed countries: since operations on countries are
  independent of each other, sharding spatial routing and demographics data and
  parallel processing can be easily implemented.
* Number of facilities: this is the main contention point of the application,
  since the demand calculation algorithm is linear in the number of affected
  facilities. Right now, we have limited the demand computation to regions
  within countries which yields near realtime performance for several hundred
  facilities. For the preprocessing portion of the algorithm, it can be easily
  paralellized.
* Number of concurrent users: can be scaled horizontally by adding more
  application servers to fulfill the requests. Most interesting operations are
  read-only and as such can be easily paralellized. Given the nature of the
  application we don't expect a huge demand on this dimension.

### Data Sources

The production deployment of [PlanWise](http://planwise.instedd.org) uses
geospatial routing information
from [OpenStreetMap](http://www.openstreetmap.org/about) and demographics
datasets from [WorldPop](http://www.worldpop.org.uk/).


## Developing

Instructions for setting up a development environment using Docker.

Build the required Docker images:

```sh
$ docker-compose build
```

Start the Docker compose stack defined in `docker-compose.yml`

```sh
$ docker-compose up
```

This will start the PostgreSQL/PostGIS database, the MapServer/MapCache
containers and a headless nREPL container.


### Mapserver

The mapserver and mapcache containers for development will use the map data in
the `data` folder. Download `KEN_popmap15_v2b.tif` from
[worldpop.org.uk](http://www.worldpop.org.uk/data/files/index.php?dataset=KEN-POP&action=group),
and place it there. Refer to the README in the `mapserver` folder for more information.


### Bootstrap the map data

Run inside the `app` container:

```sh
$ docker-compose exec app bash
app$ scripts/bootstrap-dev.sh
```

### Configure Guisso credentials

Additionally, the project requires [GUISSO](https://github.com/instedd/guisso)
information (identifier and secret) to establish the OAuth flow with resourcemap.
Register your development host in GUISSO, and set the environment variables in
a `docker-compose.override.yml`:

```
version: '2'

services:
  app:
    environment:
      - GUISSO_CLIENT_ID=YOURID
      - GUISSO_CLIENT_SECRET=YOURSECRET
```

Or you can set these values in the local `profiles.clj`, which is more useful if
you plan to run the application outside Docker (see below):

```clojure
{:profiles/dev  {:env {:guisso-url "https://login.instedd.org/"
                       :guisso-client-id "YOURID"
                       :guisso-client-secret "YOURSECRET"}}}
```

## Extra steps for running the application outside Docker

To avoid Docker from starting Leiningen, put in your
`docker-compose.override.yml` the following configuration:

```
version: '2'

services:
  app:
    command: /bin/true
```

You need to have [Leiningen](http://leiningen.org) and the [SASS
compiler](https://github.com/sass/sassc) installed. In Mac OSX using Homebrew,
just run:

```sh
$ brew install leiningen sassc
```

### Environment

The following environment variables are used by the project scripts, and it is
suggested to set them before starting development:

```
export RASTER_ISOCHRONES=false
export CALCULATE_DEMAND=false
export POSTGRES_PASSWORD="planwise"
export POSTGRES_USER=planwise
export POSTGRES_DB=planwise
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5433
```

Default values are set in the file `env`, so you can simply run:

```sh 
$ source ./env
```

### GDAL

Certain processing scripts rely on GDAL binaries for processing, with PostgreSQL
support. Install it with:

```bash
$ brew install gdal --with-postgresql
```

### Binaries

The application has some C++ binaries which are run in the context of the
application. You'll need to compile these:

```sh 
$ cd cpp
$ make clean all
```

## Development workflow with the REPL

Connect to the running REPL inside the Docker container from your editor/IDE or
from the command line:

```
$ lein repl :connect
```

Or, if running outside Docker, start up the REPL.

Load the development namespace and start the system:

```clojure
user=> (dev)
:loaded
dev=> (go)
```

By default this creates a web server at <http://localhost:3000>. Note that
these 3 commands are ran when invoking `scripts/dev`.

When you make changes to your source files, use `reset` to reload any
modified files and reset the server. Changes to CSS or ClojureScript
files will be hot-loaded into the browser.

```clojure
dev=> (reset)
:reloading (...)
:resumed
```

By default, changes to SASS or Clojurescript files will trigger a reload
automatically. You can change that behaviour by disabling the auto-builder in
`dev/dev.clj` (see the `:auto` component in `new-system`).

If you want to access a ClojureScript REPL, make sure that the site is loaded
in a browser and run:

```clojure
dev=> (cljs-repl)
Waiting for browser connection... Connected.
To quit, type: :cljs/quit
nil
cljs.user=>
```


## Further configuration information

### Database

The database engine is PostgreSQL, using PostGIS and pgRouting. It is included
in the default Docker compose file.

Database schema is managed through migrations via
[ragtime](https://github.com/duct-framework/duct-ragtime-component). Lein tasks
`lein migrate` and `lein rollback` can be issued for managing the DB schema, or
`(migrate (:ragtime system))` from the repl.

Migration files are located in `resources/migrations`, and follow the
`NUM-name.(up|down).sql` naming convention, where `NUM` is a 3-digit incremental
ID. Additionally, SQL functions located in `resources/planwise/plpgsql` are
regenerated on every `lein migrate`, or can be manually loaded from the REPL by
running `(load-sql)`.

To proceed with the setup of your development environment, after starting up
docker compose, run the database migrations to install the needed extensions and
schema:

```bash
$ lein migrate
```

As a *one-time task*, to seed your database with routing information from OSM,
run the following scripts to import routing information from any of the
supported countries, using the provided application container:

```bash
$ docker-compose run app bash
app$ scripts/import-osm osm2pgrouting kenya
app$ scripts/load-regions kenya
```

The `load-regions` script will download regions from a Mapzen data dump
extracted from OSM, load them into the DB, and optionally preprocess them.


### Testing

Running the tests require a separate scratch database.

```bash
$ docker-compose exec db createdb planwise-test
```

The connection configuration is the key `:test-database-url` configuration in
`project.clj`/`profiles.clj`.

Testing is fastest through the REPL, as you avoid environment startup time.

```clojure
dev=> (test)
...
```

But you can also run tests through Leiningen.

```sh
lein test
```

### Generators

This project has several generator functions to help you create files.

To create a new endpoint:

```clojure
dev=> (gen/endpoint "bar")
Creating file src/foo/endpoint/bar.clj
Creating file test/foo/endpoint/bar_test.clj
Creating directory resources/foo/endpoint/bar
nil
```

To create a new component:

```clojure
dev=> (gen/component "baz")
Creating file src/foo/component/baz.clj
Creating file test/foo/component/baz_test.clj
nil
```

### Importing a new country

1. Download the OpenStreetMap data for the country from
   http://download.geofabrik.de/index.html (the `.osm.bz2` version) and
   uncompress it
1. Copy the extracted `.osm` file to `/tmp/$COUNTRY.osm`
  1. If you are part of InSTEDD, recompress it as `.gz` and upload it to S3
     (`https://s3.amazonws.com/planwise/data/$COUNTRY.osm.gz`) to make it
     available to others.
  1. If you are not part of InSTEDD, include a link to download the file in your
     PR so we can re-upload it
1. Add the file name to the `COUNTRIES` list in `import-osm`
1. Download population data file from
   [WorldPop](http://www.worldpop.org.uk/data/get_data/)
  1. Search for the country and download its dataset
  1. Extract it and look for the population `.tif` file
  1. Copy it to `data/` directory
    1. Upload it to AWS or another host, zipped as `.gz` to share it
  1. Add an `elif` branch to fill `POPULATION_FILE` variable in
     `scripts/isochrone-population`
  1. Add another `case` branch in `base-raster` function of
     `scripts/regions-population`


## Deploying

Sample files for docker cloud and docker compose are provided in the root
folder, which make use of the project's [Docker
image](https://hub.docker.com/r/instedd/planwise/).

After setting up the stack, DB data can be provisioned by running the scripts
described in the _Database_ section of this document.


## Legal

Copyright © 2016 InSTEDD

This software is released under the GPLv3 license. See LICENSE.md.
