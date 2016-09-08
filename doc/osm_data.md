### OSM data

To process OSM data, install OpenStreetMap tool Osmosis (tested version is 0.44.1)
```bash
$ brew install osmosis
```

The OSM extracts can be downloaded in OSM or PBF format from http://download.geofabrik.de/africa.html

To transform the OSM data from PBF format to OSM, run:
```bash
$ osmosis --read-pbf file=kenya-latest.osm.pbf --write-xml file=kenya-latest.osm
```

This data is then imported using osm2pgRouting, as in script `import-osm`
