CFLAGS=`gdal-config --cflags`
LIBS=`gdal-config --libs`

all:
	g++ $(CFLAGS) $(LIBS) rasterize.cpp -o rast

clean:
	rm rast
