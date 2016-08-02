CFLAGS=`gdal-config --cflags`
LIBS=`gdal-config --libs`

all:
	g++ $(CFLAGS) $(LIBS) count-population.cpp -o count-population
	g++ $(CFLAGS) $(LIBS) calculate-demand.cpp -o calculate-demand

clean:
	rm count-population
	rm calculate-demand
