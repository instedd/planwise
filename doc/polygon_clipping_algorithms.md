### References

- The algorithm design manual
- Computational geometry algorithms and applications
- Computational geometry algorithms in C
  
### Primitives

- Triangulation of a polygon
  - Complexity: `O(n)`.
- Segment intersection
	- Complexity: `O(n log n + k)` where `k` is the number of intersections.
	- Reference implementation: [Code](https://github.com/ivvaan/balaban-segments-intersections)
	- [An optimal algorithm for finding segments intersections](http://www.cs.sfu.ca/~binay/813.2011/Balaban.pdf)
- Reference [implementation](http://www.cs.cmu.edu/~quake/robust.html) in C for orientation and in-circle tests 

### Polygon intersection

The intersection of non-convex polygons may have quadratic size, this implies that any
clipping algorithm that supports arbitrary polygons has at least worse case time complexity of 
`O(nm)` (`n` and `m` being the number of edges of each polygon). The worst case time complexity
of this algorithms is bounded by the segment intersection finding algorithm which gives
an `O((n+k) log n)` complexity (being `k` the number of intersections and `n = n1 + n2`).
This [paper](http://www.cs.ucr.edu/~vbz/cs230papers/martinez_boolean.pdf) shows a simple algorithm
with that time complexity.

Cases:

- Intersection of convex polygons 
	- Complexity: `O(n + m)`
  
- Vatti
	- A generic solution to polygon clipping (ACM, have PDF).
	- Allows self intersecting polygons

- [Greiner–Hormann](http://www.inf.usi.ch/hormann/papers/Greiner.1998.ECO.pdf)
	- Reference [implementation](http://davis.wpi.edu/~matt/courses/clipping/)
	- Allows self intersecting polygons

- Sutherland–Hodgman
	- The clipping polygon must be convex, it may be useful for rendering.
	- Reference [implementation](https://github.com/RandyGaul/ImpulseEngine/blob/master/Collision.cpp)

- Weiler–Atherton
  - Complicated implementation with no apparent gain in performance.
  - Original [paper](https://www.cs.drexel.edu/~david/Classes/CS430/HWs/p214-weiler.pdf)

- Comparisons based on time performance (smaller == faster): Greiner < Vatti
  
### Libraries

Options

- Clipper (Vatti)
- CGAL
- Boost.Polygon
- GEOS

Good [comparison](http://rogue-modron.blogspot.com.ar/2011/04/polygon-clipping-wrapper-benchmark.html) for reference.
