ifdef RELEASE
	CPPFLAGS=-O3 -Wall -std=c++11
	LDFLAGS=
else
	CPPFLAGS=-O0 -Wall -g -std=c++11
	LDFLAGS=
endif
INCLUDES=$(shell gdal-config --cflags)
LIBS=$(shell gdal-config --libs)

ifeq ($(shell uname), Darwin)
BOOST_VERSION=$(shell brew info --json=v1 boost | jq -r .[0].linked_keg)
LIBS += -L/usr/local/Cellar/boost/$(BOOST_VERSION)/lib/ -lboost_timer
endif

all: aggregate-population calculate-demand

aggregate-population: aggregate-population.o
	g++ $(LDFLAGS) -o $@ $+ $(LIBS)

calculate-demand: calculate-demand.o
	g++ $(LDFLAGS) -o $@ $+ $(LIBS)

%.o: %.cpp
	g++ $(CPPFLAGS) $(INCLUDES) -c $< -o $@

release: clean
	$(MAKE) RELEASE=1 all

clean:
	rm -f *.o
	rm -f aggregate-population calculate-demand

# debian:
# 	docker build -t instedd/planwise-cpp-builder:local .
# 	docker run --rm -v "`pwd`:/app/" instedd/planwise-cpp-builder make -C /app release

.PHONY: clean release debian
