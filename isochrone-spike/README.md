# Setup

1. Install PostgreSQL, PostGIS and pgRouting using Homebrew:

```$ brew install postgresql postgis pgrouting```

Tested versions at the time of this writing are:

* PostgreSQL: 9.5.2
* PostGIS: 2.2.2
* pgRouting: 2.2.1

2. (Optional) Install OpenStreetMap tool Osmosis:

```$ brew install osmosis```

Tested version is 0.44.1

3. Download the OSM extract for Kenya from
   http://download.geofabrik.de/africa/kenya.html in OSM or PBF format.

4. Download and compile `osm2pgRouting` from
   https://github.com/ggiraldez/osm2pgrouting (patched to compile in OS X). Use
   the `develop` branch:

```
$ git clone https://github.com/ggiraldez/osm2pgrouting
$ cd osm2pgRouting
$ git checkout develop
$ cmake -H. -Bbuild
$ cd build
$ make
```

Then put the generated `osm2pgrouting` binary in some directory in your PATH.

5. Create a database to hold the routing information:

```
$ createdb routing
$ psql routing
psql> create extension postgis;
psql> create extension pgrouting;
psql> \q
```

6. (Optional) If you downloaded the OSM data in PBF format, you need to
   transform it to .osm. Using Osmosis:

```$ osmosis --read-pbf file=kenya-latest.osm.pbf --write-xml
file=kenya-latest.osm```

7. Import the OSM data into the database using `osm2pgRouting`:

```$ osm2pgrouting -f kenya-latest.osm -c mapconfig.xml -d routing -U $(whoami)```

8. Obtain the sites for the health facilities from Resourcemap in JSON format to
   import into the database.

9. Import the sites list into the database:

```
$ cd viewer
$ lein import-sites ../kenya-facilities.json
```

10. Start the viewer in development mode using:

```$ lein figwheel```

and open it in http://localhost:3449
