# Coverage algorithms

Coverage algorithms should calculate the coverage area of a site in a geographic
point given some criteria. Each algorithm can apply a different strategy to
compute the resulting coverage and accept different criteria parameters.

For example, the coverage for car driving would accept a maximum driving time
and use OSM road network data. The coverage for walking distance would also
accept a maximum travel time but use a capped raster friction layer. A
hipotetical FM radio coverage algorithm would take a minimum signal strength as
a cut criteria and use the digital elevation model for the computation.

The expected output format for the coverage is both in vector format as a
polygon and in raster format. The algorithms should accept parameters
controlling the resolution and/or quality of the output. Eg. a simplification
threshold for vector responses, grid resolution and origin/snap coordinates for
raster responses and spatial reference system for both.

Several instances of a single algorithm can be declared, each with a different
data set, for example to compute coverages in different areas, or with different
precision. Hence, each instance of an algorithm is only aplicable to a
restricted geographical area. The choice of data set may also constrain the
allowed criteria parameters, depending on the algorithm implementation.
