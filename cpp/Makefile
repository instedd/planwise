CFLAGS=`gdal-config --cflags`
LIBS=`gdal-config --libs`

all:
	g++ $(CFLAGS) count-population.cpp -o count-population $(LIBS)
	g++ $(CFLAGS) calculate-demand.cpp -o calculate-demand $(LIBS)

clean:
	rm count-population
	rm calculate-demand
