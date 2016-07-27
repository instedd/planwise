import os

width = 9601
height = 12179
tilesize = 2048
filename = 'KEN_popmap15_v2b'

os.chdir('./data')

for i in range(0, width, tilesize):
  for j in range(0, height, tilesize):
    size_x = (width - i) if i + tilesize > width else tilesize
    size_y = (height - j) if j + tilesize > height else tilesize
    gdaltranString = "gdal_translate -of GTIFF -srcwin {0} {1} {2} {3} {4}.tif {4}_{0}_{1}.tif".format(i, j, size_x, size_y, filename)
    os.system(gdaltranString)
    os.system("gdaladdo {0}_{1}_{2}.tif -r average 2 4 8 16 32".format(filename, i, j))

os.system("gdaltindex {0}.shp {0}_*".format(filename))
