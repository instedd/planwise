# Dependencies

## osm2pgrouting

Version 2.2 or later works. But the configuration XML format for version 2.3
(current Homebrew version) is different from 2.2 (current Debian/Ubuntu
version). See the locally installed `mapconfig_for_cars.xml` (usually in
`/usr/share/osm2pgrouting` or
`/usr/local/opt/osm2pgrouting/share/osm2pgrouting`)
 
## osmconvert

This is used for pre-processing the OSM extract, since it's usually very big and
it makes `osm2pgrouting` consume too much RAM. Version 0.8, compiled from source
works.

```sh
git clone https://gitlab.com/osm-c-tools/osmctools.git
cd osmctools
git checkout 0.8
autoreconf --install
./configure
make && make install
```
 
# Importing OSM data

 - Obtain the osm2pgrouting XML config file `mapconfig_for_cars.xml` and make
   sure the format corresponds with the version of the binary you have
   available.
 
 - Download the OSM file for the requested area. The site
   http://download.geofabrik.de/index.html has up-to-date extracts for all
   countries in the world.

 - Pre-process with `osmconvert` to reduce file size and remove unneeded data:
 ```sh
 osmconvert XXX-latest.osm.pbf --drop-author --drop-version --out-osm -o=XXX.osm
 ```

 - Import the file into pgRouting:
 
 ```sh
 osm2pgrouting -c mapconfig.xml -f XXX.osm -d DB_NAME -h DB_HOST -p DB_PORT -U DB_USER -W DB_PASSWORD --no-index
 ```
 
 The option `--clean` can be passed in as well to drop the relevant tables
 before importing from OSM.

 - Apply fixes and post-processing in pgRouting. This must be run after
   importing any new data into pgRouting, but can be done after importing
   several extracts repeating the commands above.
 
 ```sql
 SELECT pgr_analyzeGraph('ways', 0.001, 'the_geom', 'gid');
 SELECT fixpoint_connect_isolated_segments(5000);

 SELECT populate_ways_nodes();

 UPDATE ways SET length_m = st_length(the_geom::geography) WHERE length_m IS NULL;
 SELECT apply_traffic_factor(1.5);
 ``` 
 
 `pgr_analyzeGraph` is needed to fill the `chk` and `cnt` fields in the
 `ways_vertices_pgr` table which is later used by
 `fixpoint_connect_isolated_segments` to try and join isolated ways segments to
 the rest of the routing network.
 
 `populate_ways_nodes` builds the table `ways_nodes` from scratch to use by the
 alpha shape algorithm.

 `apply_traffic_factor` simply scales up the cost of traversal of each graph
 edge. The factor is an approximation obtained empirically using Google Maps
 directions costs as reference.
 
# Backing up data for export

This commands export and import the pgRouting tables using the PostgreSQL custom
format. While this is the most flexible format, it *will not work* if exporting
from a newer version into a previous version.

In that case, use the standard SQL script format and restore using `psql`. Keep
in mind in that case, the `-a` for data-only and `-c` for re-create table must
be provided to `pg_dump` instead.

```sh
pg_dump -d DB_NAME -h DB_HOST -p DB_PORT -U DB_USER -Fc -v -f BACKUP_FILE -t ways -t ways_vertices_pgr -t ways_nodes -t configuration
```

List the contents of the backup:

```sh
pg_restore -l BACKUP_FILE
```

# Restoring data from pg_dump

```sh
pg_restore -d DB_NAME -h DB_HOST -p DB_PORT -U DB_USER -Fc -v BACKUP_FILE
```

Data-only restore:

```sh
pg_restore -d DB_NAME -h DB_HOST -p DB_PORT -U DB_USER -Fc -v -a BACKUP_FILE
```

Drop tables before restore:

```sh
pg_restore -d DB_NAME -h DB_HOST -p DB_PORT -U DB_USER -Fc -v -c BACKUP_FILE
```
