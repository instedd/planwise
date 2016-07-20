# PlanWise

Concern's Facility Planner. This is a tool to support decision makers in
choosing the location of future health-care or social aid facilities and/or
improvement of existing ones to maximize their impact in the community.

## Developing

### Setup

You need to have [Leiningen](http://leiningen.org) and the
[SASS compiler](https://github.com/sass/sassc) installed. In Mac OS X using
Homebrew, just run:

```sh
brew install leiningen sassc
```

When you first clone this repository, run:

```sh
lein setup
```

This will create files for local configuration, and prep your system
for the project.

Next create a development database:

```bash
$ createdb routing
```

And configure the connection URL in your `profiles.clj`:

```clojure
;; Local profile overrides
{:profiles/dev  {:env {:database-url "jdbc:postgresql://localhost/routing"}}}
```

See `doc/Database-Setup.md` for further database configuration.


### Environment

To begin developing, start with a REPL.

```sh
lein repl
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

By default this creates a web server at <http://localhost:3000>.

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

### Database

Database schema is managed through migrations via [ragtime](https://github.com/duct-framework/duct-ragtime-component). Lein tasks `lein migrate` and `lein rollback` can be issued for managing the DB schema.

Migration files are located in `resources/migrations`, and follow the `NUM-name.(up|down).sql` naming convention, where `NUM` is a 3-digit incremental ID.

Beside the migrations for controlling the schema, the DB can be populated through the following scripts, configured to run on Kenya:
* `import-osm` will download and process via `osm2pgrouting` OpenStreetMap data
* `import-sites` will download ResourceMap facilities data and import them
* `load-regions` will import a geojson from Mapzen to load regions (Kenya by default)
* `preprocess-isochrones` can be ran any number of times with different parameters to calculate the isochrones for the facilities; for instance, `preprocess-isochrones 300 buffer 60 180 15` will calculate the isochrones using the buffer method (with a 300m threshold) for driving distances from 1 to 3 hours, at 15 min intervals.

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

After setting up the stack, DB data can be provisioned by running the 3 scripts described in the _Database_ section of this document.

## Legal

Copyright © 2016 InSTEDD

See LICENSE.md.
