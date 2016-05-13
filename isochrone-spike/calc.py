from concern import *
import cPickle

print "Loading network..."
with open('road_network.dat', 'rb') as f:
    network = cPickle.load(f)

print "Calculating isochrone..."
print network.isochrone(2473334109, .5)
