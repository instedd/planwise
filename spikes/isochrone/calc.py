from concern import *
import cPickle

def load_network():
    with open('road_network.dat', 'rb') as f:
        network = cPickle.load(f)
    return network
