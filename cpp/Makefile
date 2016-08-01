CFLAGS=`gdal-config --cflags`
LIBS=`gdal-config --libs`

all:
	g++ $(CFLAGS) $(LIBS) count-population.cpp -o count-population

clean:
	rm count-population
