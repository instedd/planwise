import sys
from LatLon import LatLon, Latitude, Longitude

ROAD_MAXSPEED = {
    'motorway_link': 100,
    'motorway': 100,
    'raceway': 100,

    'trunk': 70,
    'trunk_link': 70,

    'primary': 60,
    'primary_link': 60,

    'secondary': 50,
    'secondary_link': 50,

    'tertiary': 40,
    'tertiary_link': 40,

    'service': 30,
    'residential': 30,
    'unclassified': 30,
    'road': 30,

    'living_street': 20,
    'cycleway': 20,
    'track': 20,

    'pedestrian': 6,
    'bridleway': 6,
    'footway': 6,
    'ford': 6,
    'path': 6,

    'construction': 10,
    'services': 10,
    'steps': 10
}

class RoadNetwork(object):
    nodes = {}
    adjacency = {}

    def isochrone(self, origin, time):
        time_to_node = set([(0, origin)])
        node_to_time = {origin: 0}
        visited = set()
        while len(time_to_node) > 0:
            closest = min(time_to_node)
            time_to_node.remove(closest)

            if closest[0] > time:
                break

            if closest[1] in self.adjacency:
                for neighbour, ht, d in self.adjacency[closest[1]]:
                    if neighbour in visited: continue
                    time_to_neigh = self.takes_time(ht, d)+closest[0]
                    if neighbour not in node_to_time:
                        node_to_time[neighbour] = time_to_neigh
                        time_to_node.add((time_to_neigh, neighbour))
                    elif time_to_neigh < node_to_time[neighbour]:
                        time_to_node.remove((node_to_time[neighbour], neighbour))
                        node_to_time[neighbour] = time_to_neigh
                        time_to_node.add((time_to_neigh, neighbour))

            visited.add(closest[1])

        return node_to_time

    def takes_time(self, ht, d):
        return d/ROAD_MAXSPEED[ht]

    def isochrone_to_csv(self, isochrone, filename):
        coords = [self.nodes[x] for x in isochrone.keys()]

        with open(filename, 'w') as f:
            f.write("# lat,lon\n")
            for lon, lat in coords:
                f.write(str(lat) + "," + str(lon) + "\n")

    def closest_to(self, lat, lon):
        origin = LatLon(Latitude(lat), Longitude(lon))
        min_dist = sys.maxint
        closest = None
        for node_id, (node_lat, node_lon) in self.nodes.iteritems():
            node_position = LatLon(Latitude(node_lat), Longitude(node_lon))
            dist_to_origin = origin.distance(node_position)
            if dist_to_origin < min_dist:
                min_dist = dist_to_origin
                closest = node_id

        return closest

