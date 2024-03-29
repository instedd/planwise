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
* [shadow-cljs](http://shadow-cljs.org/) for compilation.

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
demographics datasets from [WorldPop](http://www.worldpop.org.uk/).


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

Some scripts might require a bit more than 2gb of memory. Increase the default docker limit
if import-osm is run inside a container.

### Mapserver

The mapserver and mapcache containers for development will use the map data in
the `data` folder.

### Bootstrap the database

Run inside the `app` container:

```sh
$ docker-compose run app bash
app$ scripts/bootstrap-dev.sh
```

### Seed the database and data directory

There is a bit of geographical data needed to have a functional environment.

First, the global friction layer needs to be download as described
[here](./scripts/friction/README.md). This is used to compute walking
and car travel time.

Second, the administrative hierarchy of selected countries needs to be
downloaded. Follow [this procedure](./scripts/geojson/README.md) to populate
the `data/geojson` directory.

Third, download and register country population datasets to use as demand
raster source. While doing this last step the administrative hierarchies
will be registered and friction layer will be sliced per country. If you
don't need demand raster sources you will still need to register the
administrative hierarchies and slice the friction layer. Check
[this procedure](./scripts/population/README.md) to see how these steps are done.

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

Or you can set these values in the local `dev/resources/local.edn`, which is
more useful if you plan to run the application outside Docker (see below):

```clojure
{:duct.core/include ["dev.edn"]

 :planwise.component/auth
 {:guisso-client-id     "YOURID"
  :guisso-client-secret "YOURSECRET"}}
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

You need to have [Leiningen](http://leiningen.org) installed. In Mac OSX using
Homebrew, just run:

```sh
$ brew install leiningen
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

The project needs GDAL 2.x with support for PostgreSQL and Java bindings. On Mac
OSX you can use the osgeo4mac Homebrew tap to get it.

```bash
$ brew tap osgeo/osgeo4mac
$ brew install gdal2 --with-swig-java --with-postgresql
```

Since `gdal2` is keg-only, you need to force link it with `brew link
--force gdal2` and add `/usr/local/opt/gdal2/bin` to your PATH environment
variable.

You also need to add the make the JNI libraries discoverable by the JVM. For
development, an easy and non-intrusive way of doing it is adding the
`java.library.path` system property in your `profiles.clj`. Eg.

```clojure
;; Local profile overrides
{:profiles/dev {:jvm-opts ["-Djava.library.path=/usr/local/opt/gdal2/lib"]}}
```

### Binaries

The application has some C++ binaries which are run in the context of the
application. If running the application outside Docker, you'll need to compile
these. Otherwise, these are automatically built by `bootstrap-dev.sh`.

```sh
$ brew install cmake boost
$ scripts/build-binaries
```

### Node modules

NPM dependencies are handled by `npm` and updated via the `package.json` file.

Install NPM dependencies before firing up the REPL or compiling the project:

```sh
$ npm install
```

NB: `npm install` is ran automatically when executing `(go)` from the REPL.


## Development workflow with the REPL

Connect to the running REPL inside the Docker container from your editor/IDE or
from the command line:

```
$ lein repl :connect
```

Or, if running outside Docker, start up the REPL with `lein repl`.

Load the development namespace and start the system:

```clojure
user=> (dev)
:loaded
dev=> (go)
```

By default this creates a web server at <http://localhost:3000>. Note that
these 3 commands are ran when invoking `scripts/dev`.

When you make changes to your source files, use `reset` to reload any
modified files and reset the server.

```clojure
dev=> (reset)
:reloading (...)
:resumed
```

If you want to access a ClojureScript REPL, make sure that the server is
running and there is a browser with the application loaded.

```
$ shadow-cljs cljs-repl :app
shadow-cljs - config: /app/client/shadow-cljs.edn  cli version: 2.8.59  node: v9.11.2
shadow-cljs - connected to server
cljs.user=> (js/alert "hi")
nil
```

The `shadow-cljs` is installed by `./client/package.json` in
`./client/node_modules/.bin/shadow-cljs`. In a docker development environment
you can execute shadow-cljs from the client service container.

## Further configuration information

### Database

The database engine is PostgreSQL, using PostGIS and pgRouting. It is included
in the default Docker compose file.

Database schema is managed through migrations via
[ragtime](https://github.com/duct-framework/duct-ragtime-component). In
development, they are run automatically on system startup and after every
`(reset)`.

With a running system, there is a `(rollback-1)` function to manually rollback
the last migration, though will be rarely needed since the migrations are
rebased automatically on each reset. The expected workflow is:

 - create a new migration, adding both the up and down SQL scripts
 - run `(reset)` to apply the migration
 - modify the migration
 - run `(reset)` again, to rollback the old version and apply the modified
   migration

The function `(rollback-1)` can be used to manually rollback until a specific
migration. This would be needed if switching the development branch *and*
restarting the REPL at the same time. Otherwise the system should be able to
rebase the migrations when resetting.

Migrations can be executed outside the REPL via the `lein migrate` task.

Migration files are located in `resources/migrations`, and follow the
`NUM-name.(up|down).sql` naming convention, where `NUM` is a 3-digit incremental
ID.

Additionally, SQL functions located in `resources/planwise/plpgsql` are
regenerated on every `lein migrate`, or can be manually loaded from the REPL by
running `(load-sql)`.


### Testing

Running the tests require a separate scratch database.

```bash
$ docker-compose exec db createdb planwise-test -U planwise
```

The connection configuration is located in the environment variable
`TEST_DATABASE_URL`, with the default being as specified in
`test/resources/test.edn`.

Testing is fastest through the REPL, as you avoid environment startup time.

```clojure
dev=> (test)
...
```

But you can also run tests through Leiningen.

```sh
lein test
```

### Importing a new country

Use the Planwise Tools Docker image to manage geographic and base source sets in the database. In development, this can be spawned by running:

```sh
docker-compose run tools
```

Then follow the instructions given in `scripts/tools/README.md`.


### Intercom

Planwise supports Intercom as its CRM platform. To load the Intercom chat widget, simply start Planwise with the env variable `INTERCOM_APP_ID` set to your Intercom app id (https://www.intercom.com/help/faqs-and-troubleshooting/getting-set-up/where-can-i-find-my-workspace-id-app-id).

Planwise will forward any conversation with a logged user identifying them through their email address. Anonymous, unlogged users will also be able to communicate.

If you don't want to use Intercom, you can simply omit `INTERCOM_APP_ID` or set it to `''`.

To test the feature in development, add the `INTERCOM_APP_ID` variable and its value to the corresponding `edn` file.

## Deploying

Sample files for docker cloud and docker compose are provided in the root
folder, which make use of the project's [Docker
image](https://hub.docker.com/r/instedd/planwise/).

After setting up the stack, DB data can be provisioned by running the scripts
described in the _Database_ section of this document.


## Legal

Copyright © 2016 InSTEDD

This software is released under the GPLv3 license. See LICENSE.md.
