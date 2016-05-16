from imposm.parser import OSMParser
from LatLon import LatLon, Latitude, Longitude
import cPickle
from concern import *

class Collector(object):
    nodes = {}
    refs = set()
    adjacency = {}
    edge_count = 0

    def coords_callback(self, coords):
        for osmid, lon, lat in coords:
            self.nodes[osmid] = (lon, lat)

    def add_edges(self, nodes, ht):
        for n1, n2 in nodes:
            if n1 not in self.adjacency:
                self.adjacency[n1] = []
            self.adjacency[n1].append((n2, ht))
            self.edge_count += 1

    def ways_callback(self, ways):
        for osmid, tags, refs in ways:
            if 'highway' in tags:
                for ref in refs:
                    self.refs.add(ref)

                ht = tags['highway']
                oneway = 'oneway' in tags and tags['oneway'] == 'yes'
                self.add_edges(zip(refs, refs[1:]), ht)
                if not oneway:
                    self.add_edges(zip(refs[1:], refs), ht)

    def used_nodes(self):
        return dict([(k, self.nodes[k]) for k in self.refs])



def build_road_network(collector):
    r = RoadNetwork()
    r.nodes = collector.used_nodes()
    r.adjacency = collector.adjacency
    return r

def calculate_distances(network):
    for n1 in network.adjacency.iterkeys():
        node1 = network.nodes[n1]
        p1 = LatLon(Latitude(node1[1]), Longitude(node1[0]))
        updated = []
        for n2, ht in network.adjacency[n1]:
            node2 = network.nodes[n2]
            p2 = LatLon(Latitude(node2[1]), Longitude(node2[0]))
            d = p1.distance(p2)
            updated.append((n2, ht, d))
        network.adjacency[n1] = updated

collector = Collector()
p = OSMParser(concurrency=4, coords_callback=collector.coords_callback, ways_callback=collector.ways_callback)
p.parse('kenya-latest.osm.pbf')

print "Total coords:", len(collector.nodes)
print "Ref'ed coords:", len(collector.refs)
print "Total edges:", collector.edge_count

print "Building network..."
network = build_road_network(collector)

print "Calculating distances..."
calculate_distances(network)

with open('road_network.dat', 'wb') as f:
    cPickle.dump(network, f)

print "Done!"
