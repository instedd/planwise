# PlanWise

PlanWise applies algorithms and geospatial optimisation techniques to existing data on population, road networks and health facilities, so health care planners can better understand the unmet health needs of their constituents and better locate future health facilities. Together, InSTEDD and Concern Worldwide are developing PlanWise, to apply such algorithms to help governments visualize how potential future facilities will improve access to healthcare in underserved areas.

##Technology

###Tech stack

###Scalability

## Developing

Instructions for setting up a development environment, focused on OSX.

### Setup

You need to have [Leiningen](http://leiningen.org) and the
[SASS compiler](https://github.com/sass/sassc) installed. In Mac OSX using
Homebrew, just run:

```sh
$ brew install leiningen sassc
```

When you first clone this repository, run:

```sh
$ lein setup
```

This will create files for local configuration, and prep your system
for the project.

### Environment

The following environment variables are used by the project scripts, and it is
suggested to set them before starting development:
```
export RASTER_ISOCHRONES=false
export CALCULATE_DEMAND=false
export DATABASE_URL="jdbc:postgresql://localhost/routing?user=USERNAME"
export POSTGRES_PASSWORD=""
export POSTGRES_USER=USERNAME
export POSTGRES_DB=routing
export POSTGRES_HOST=localhost
```

Additionally, the project requires [GUISSO](https://github.com/instedd/guisso)
information (identifier and secret) to establish the OAuth flow with resourcemap.
Register your development host in GUISSO, and set the env vars:
```
export GUISSO_CLIENT_ID=YOURID
export GUISSO_CLIENT_SECRET=YOURSECRET
```

Instead of controlling them via env vars, you can set these values in the
local `profiles.clj`:
```clojure
{:profiles/dev  {:env {:guisso-url "https://login.instedd.org/"
                       :guisso-client-id "YOURID"
                       :guisso-client-secret "YOURSECRET"}}}
```

### GDAL

Certain processing scripts rely on GDAL binaries for processing, with PostgreSQL support. Install it with:
```bash
$ brew install gdal --with-postgresql
```

### Database

The database engine is PostgreSQL, using PostGIS and pgRouting. Install them via:
```bash
$ brew install postgresql postgis pgrouting
```

Tested versions at the time of this writing are:

* PostgreSQL: 9.5.2
* PostGIS: 2.2.2
* pgRouting: 2.2.1

Database schema is managed through migrations via [ragtime](https://github.com/duct-framework/duct-ragtime-component). Lein tasks `lein migrate` and `lein rollback` can be issued for managing the DB schema, or `(migrate (:ragtime system))` from the repl.

Migration files are located in `resources/migrations`, and follow the `NUM-name.(up|down).sql` naming convention, where `NUM` is a 3-digit incremental ID. Additionally, SQL functions located in `resources/planwise/plpgsql` are regenerated on every `lein migrate`, or can be manually loaded from the REPL by running `(load-sql)`.

To proceed with the setup of your development environment, after installing PostgreSQL and the required extensions, create a database:
```bash
$ createdb routing
```

Configure the connection URL in your `profiles.clj`:
```clojure
;; Local profile overrides
{:profiles/dev  {:env {:database-url "jdbc:postgresql://localhost/routing"}}}
```

As a one-time task, to seed your database with routing information from OSM, run the following script to import routing information from any of the supported countries:
```bash
$ scripts/import-osm osx/osm2pgrouting kenya
```

And run the migrations:
```bash
$ lein migrate
```

Finally load regions as follows:
```bash
$ scripts/load-regions kenya
```

The `import-osm` script will download the OSM dump and import it via osm2pgrouting. Note that the binary in the `osx` folder of the repository was compiled for OSX, and was generated from [a fork](https://github.com/ggiraldez/osm2pgrouting) of the project. It can be rebuilt by running:
```bash
$ git clone https://github.com/ggiraldez/osm2pgrouting
$ cd osm2pgRouting
$ git checkout develop
$ cmake -H. -Bbuild
$ cd build
$ make
```

The `load-regions` script will download regions from a Mapzen data dump extracted from OSM, load them into the DB, and optionally preprocess them.

### Mapserver

Start mapserver and mapcache containers for development by running `docker-compose up` in the `mapserver` folder, after downloading file `KEN_popmap15_v2b.tif` from [worldpop.org.uk](http://www.worldpop.org.uk/data/files/index.php?dataset=KEN-POP&action=group), and placing it in the `data` folder. Refer to the README in that folder for more information.

### REPL

To begin developing, start with a REPL.

```sh
$ lein repl
```

Then load the development environment.

```clojure
user=> (dev)
:loaded
```

Run `go` to initiate and start the system.

```clojure
dev=> (go)
:started
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

### Testing

Running the tests require a separate scratch database. No setup is necessary
beyond creation though, as the migrations are executed automatically upon
running the tests.

```bash
$ createdb routing-test
```

And add the connection configuration in the test-database-url configuration in
your `profiles.clj`:

```clojure
;; Local profile overrides
{:profiles/dev  {:env {:database-url "jdbc:postgresql://localhost/routing"
                       :test-database-url "jdbc:postgresql://localhost/routing-test"}}}
```

Testing is fastest through the REPL, as you avoid environment startup
time.

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

## Deploying

Sample files for docker cloud and docker compose are provided in the root folder, which make use of the project's [Docker image](https://hub.docker.com/r/instedd/planwise/).

After setting up the stack, DB data can be provisioned by running the scripts described in the _Database_ section of this document.

## Running with docker

There is a set of docker-compose files for locally running the application; one
of them used for a first-time setup of the database, and other for regularly
running the application.

1. Create `.docker-env` file with GUISSO_CLIENT_SECRET and GUISSO_CLIENT_ID env vars, after registering your app in [GUISSO](https://github.com/instedd/guisso)
2. Run `docker-compose -f docker-compose.setup.yml up` to set up the environment
3. After `planwise_setup_1` exits successfully, run `docker-compose up` to start the app in port 3000

## Legal

Copyright © 2016 InSTEDD

This software is released under the GPLv3 license. See LICENSE.md.
