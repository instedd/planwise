CFLAGS=`gdal-config --cflags`
LIBS=`gdal-config --libs`

all:
	g++ $(CFLAGS) -O0 -g -Wall aggregate-population.cpp -o aggregate-population $(LIBS)
	g++ $(CFLAGS) -O0 -g -Wall calculate-demand.cpp -o calculate-demand $(LIBS)

release:
	g++ $(CFLAGS) -O3 -Wall aggregate-population.cpp -o aggregate-population $(LIBS)
	g++ $(CFLAGS) -O3 -Wall calculate-demand.cpp -o calculate-demand $(LIBS)

clean:
	rm aggregate-population
	rm calculate-demand

debian:
	docker build -t instedd/planwise-cpp-builder:local .
	docker run --rm -v "`pwd`:/app/" 0360aa208e49 make -C /app release
