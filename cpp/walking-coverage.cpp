#include "boost/program_options.hpp"
#include "boost/filesystem.hpp"
#include "boost/algorithm/string.hpp"
#include "boost/heap/binomial_heap.hpp"
#include "boost/timer/timer.hpp"

#include "gdal_priv.h"
#include "cpl_conv.h"
#include "ogr_geometry.h"
#include "ogrsf_frmts.h"

#include <iostream>
#include <string>
#include <sstream>
#include <stdexcept>
#include <list>
#include <vector>
#include <functional>

using namespace std;


// ====== Generic utilities

template<typename T> inline bool
between(const T& value, const T& x1, const T& x2)
{
  if (x1 > x2) {
    return value >= x2 && value <= x1;
  } else {
    return value >= x1 && value <= x2;
  }
}



// ===== Coordinates

typedef pair<double,double> coords_t;

inline coords_t
make_coords(double lng, double lat)
{
  return make_pair(lng, lat);
}

typedef pair<int,int> pixel_coords_t;

inline pixel_coords_t
make_pixel_coords(int x, int y)
{
  return make_pair(x, y);
}

template<typename T>
ostream& operator<<(ostream& os, const pair<T,T>& p) {
  os << p.first << "," << p.second;
  return os;
}


// ===== Raster adapter

class raster_t {
  GDALDataset *_dataset;
  double _geoTransform[6];

public:
  raster_t(GDALDataset *poDataset) : _dataset(poDataset) {
    if (poDataset == NULL) {
      throw invalid_argument("dataset cannot be NULL");
    }
    if (poDataset->GetGeoTransform(_geoTransform) != CE_None) {
      throw invalid_argument("cannot get geotransform for raster");
    }
  }
  virtual ~raster_t() {
    GDALClose(_dataset);
  }

  bool is_north_up() const {
    return _geoTransform[2] == 0 && _geoTransform[4] == 0
      && _geoTransform[1] > 0 && _geoTransform[5] < 0;
  }
  coords_t top_left_coords() const {
    return make_coords(_geoTransform[0], _geoTransform[3]);
  }
  coords_t bottom_right_coords() const {
    return make_coords(_geoTransform[0] + _geoTransform[1] * _dataset->GetRasterXSize(),
                       _geoTransform[3] + _geoTransform[5] * _dataset->GetRasterYSize());
  }
  int x_size() const { return _dataset->GetRasterXSize(); }
  int y_size() const { return _dataset->GetRasterYSize(); }
  int band_count() const { return _dataset->GetRasterCount(); }
  double pixel_width() const { return _geoTransform[1]; }
  double pixel_height() const { return -_geoTransform[5]; }
  GDALDataset *dataset() const { return _dataset; }
  GDALDriver *driver() const { return _dataset->GetDriver(); }
  float pixel_width_meters() const {
    float lngDegDistInMEquator = 111111.0f;
    float centerLat = (top_left_coords().second + bottom_right_coords().second) / 2;
    return lngDegDistInMEquator * cos(centerLat) * pixel_width();
  }
  float pixel_height_meters() const {
    float latDegDistInM = 111111.0f;
    return latDegDistInM * pixel_height();
  }

  const bool contains(const coords_t& point) const {
    const coords_t tl(top_left_coords());
    const coords_t br(bottom_right_coords());
    return between(point.first, tl.first, br.first)
      && between(point.second, tl.second, br.second);
  }

  pixel_coords_t pixel_coords(const coords_t& lnglat) const {
    const coords_t tl(top_left_coords());

    return make_pixel_coords((lnglat.first - tl.first) / pixel_width(),
                             (lnglat.second - tl.second) / -pixel_height());
  }
};



// ===== Utility functions

static void
show_raster_info(const raster_t &raster)
{
  cout << "Driver: " << raster.driver()->GetDescription()
       << "/" << raster.driver()->GetMetadataItem(GDAL_DMD_LONGNAME)
       << endl;

  cout << "Raster size: " << raster.x_size()
       << "x" << raster.y_size()
       << "x" << raster.band_count()
       << endl;

  if (raster.dataset()->GetProjectionRef() != NULL) {
    cout << "Projection: " << raster.dataset()->GetProjectionRef() << endl;
  } else {
    cout << "No projection data" << endl;
  }

  coords_t tl(raster.top_left_coords());
  cout.precision(6);
  cout << "Origin: " << fixed << tl << endl;
  cout << "Pixel size: " << fixed << raster.pixel_width() << "," << raster.pixel_height() << endl;
  cout << "Aproximate pixel size (meters): " << fixed
       << raster.pixel_width_meters() << ","
       << raster.pixel_height_meters() << endl;
}



inline double
parse_double(const string& s)
{
  istringstream i(s);
  double x;
  char c;
  if (!(i >> x) || i.get(c)) {
    throw runtime_error("number parse error on '" + s + "'");
  }
  return x;
}

static coords_t
parse_coordinates(const string& s)
{
  vector<string> numbers;
  boost::split(numbers, s, [](char c) { return c == ','; });
  if (numbers.size() != 2) {
    throw runtime_error("invalid coordinates '" + s + "'");
  }

  return make_coords(parse_double(numbers[0]), parse_double(numbers[1]));
}



// ===== Command line parsing

struct run_options_t {
  string   _rasterPath;
  string   _outputCostPath;
  string   _outputIsochronePath;
  coords_t _origin;
  int      _maxTimeCost;    // defaults to 180 minutes = 3 hours
  float    _minFriction;    // defaults to 0.01 min/m = 6 km/h (ie. walking speed)
};

static bool
parse_command_line(int argc, char *argv[], run_options_t& options)
{
  namespace po = boost::program_options;

  const string appName = boost::filesystem::basename(argv[0]);

  po::options_description desc("Options");
  desc.add_options()
    ("help,h", "Print help message")
    ("friction-raster,r", po::value<string>(), "input friction raster file")
    ("cost-raster,c", po::value<string>(), "output cost raster file")
    ("isochrone-vector,i", po::value<string>(), "output isochrone vector file")
    ("origin,o", po::value<string>(), "coordinates of origin given in lng,lat format")
    ("max-time,m", po::value<int>()->default_value(180), "maximum time given in minutes")
    ("min-friction,f", po::value<float>()->default_value(0.01), "minimum friction to consider in min/m");

  po::variables_map vm;

  try {
    po::store(po::command_line_parser(argc, argv)
              .options(desc)
              .run(),
              vm);

    if (vm.count("help")) {
      cout << "Usage:" << endl
           << "  " << appName << " [options]" << endl
           << desc << endl;
      return true;
    }

    if (!vm.count("friction-raster")) {
      cerr << "ERROR: missing friction raster option" << endl;
      cerr << "Run with --help for available options" << endl;
      return false;
    }

    if (!vm.count("origin")) {
      cerr << "ERROR: missing origin coordinates" << endl;
      cerr << "Run with --help for available options" << endl;
      return false;
    }

    options._rasterPath = vm["friction-raster"].as<string>();
    options._origin = parse_coordinates(vm["origin"].as<string>());
    options._maxTimeCost = vm["max-time"].as<int>();
    options._minFriction = vm["min-friction"].as<float>();

    if (vm.count("cost-raster")) {
      options._outputCostPath = vm["cost-raster"].as<string>();
    }
    if (vm.count("isochrone-vector")) {
      options._outputIsochronePath = vm["isochrone-vector"].as<string>();
    }

  } catch (exception& e) {
    cerr << "ERROR: " << e.what() << endl;
    cerr << "Run with --help for available options" << endl;
    return false;
  }

  return true;
}


// ======== Main algorithm

struct xy_with_cost_t {
  int _x;
  int _y;
  float _cost;

  inline explicit xy_with_cost_t(int x, int y, float cost) : _x(x), _y(y), _cost(cost) {}

  inline bool operator<(const xy_with_cost_t& other) const {
    // we want the least costly element at the top of the priority queue
    return other._cost < _cost;
  }

};

static unique_ptr<float[]>
run_dijkstra_on_friction_layer(const float* pFrictionData,
                               const int width,
                               const int height,
                               const float pixelWidthMeters,
                               const float pixelHeightMeters,
                               const int originX,
                               const int originY,
                               const float maxCost,
                               const float minFriction = 0.0f)
{
#ifdef BENCHMARK
  boost::timer::auto_cpu_timer t(std::cerr, 6, "run_dijkstra_on_friction_layer: %t sec CPU, %w sec real\n");
#endif

  // initialize (lazily?) cost layer to infinity C[x] <- inf forall x
  unique_ptr<float[]> pCost(new float[width * height]);
  fill(&pCost[0], &pCost[width * height], numeric_limits<float>::infinity());

  // add origin to priority queue Q and set the cost of origin to 0 C[o] = 0
  typedef typename boost::heap::binomial_heap<xy_with_cost_t> queue_t;
  typedef typename queue_t::handle_type handle_t;
  queue_t queue;
  pCost[originX + originY * width] = 0;
  queue.push(xy_with_cost_t(originX, originY, 0));

  vector<handle_t> handles(width * height);

  int visited = 0;

  const float horizCost = pixelWidthMeters;
  const float vertCost = pixelHeightMeters;
  const float diagCost = sqrt(horizCost * horizCost + vertCost * vertCost);

  // while Q is not empty
  while (!queue.empty()) {
    // remove the location x with the least cost from Q
    xy_with_cost_t x = queue.top();
    queue.pop();

    visited++;

    // for all neighbours n of x
    float fx = pFrictionData[x._x + width * x._y];
    fx = max(fx, minFriction);
    int nx1 = x._x > 0 ? x._x - 1 : x._x;
    int nx2 = x._x < width-1 ? x._x + 1 : x._x;
    int ny1 = x._y > 0 ? x._y - 1 : x._y;
    int ny2 = x._y < height-1 ? x._y + 1 : x._y;
    for (int nx = nx1; nx <= nx2; nx++) {
      for (int ny = ny1; ny <= ny2; ny++) {
        if (nx == x._x && ny == x._y) continue;
        float d_cost = (nx == x._x) ? vertCost : ((ny == x._y) ? horizCost : diagCost);

        // compute the cost from x to n d(x,n) and C' <- C[x] + d(x,n)
        float cn = pCost[nx + width * ny];
        float fn = pFrictionData[nx + width * ny];
        fn = max(fn, minFriction);
        float dxn = (fx + fn) / 2 * d_cost;
        // friction is given in minutes/meter
        float cn_from_x = x._cost + dxn;

        // if C[n] > C', C[n] <- C'
        if (cn > cn_from_x) {
          pCost[nx + width * ny] = cn_from_x;

          // if C' < maxCost, add (or update) n to the visit queue
          if (cn_from_x < maxCost) {
            handle_t handle = handles[nx + width * ny];
            if (handle == handle_t()) {
              handles[nx + width * ny] = queue.push(xy_with_cost_t(nx, ny, cn_from_x));
            } else {
              queue.update(handle, xy_with_cost_t(nx, ny, cn_from_x));
            }
          }
        }
      }
    }
  }

#ifdef BENCHMARK
  cout << "visited " << visited << endl;
#endif

  // return the resulting C layer
  return pCost;
}

static unique_ptr<float[]>
load_friction_data(GDALDataset *pDataset, int rasterNumber, int* pWidth, int *pHeight)
{
#ifdef BENCHMARK
  boost::timer::auto_cpu_timer t(std::cerr, 6, "load_friction_data: %t sec CPU, %w sec real\n");
#endif

  GDALRasterBand *pRasterBand = pDataset->GetRasterBand(rasterNumber);
  *pWidth = pRasterBand->GetXSize();
  *pHeight = pRasterBand->GetYSize();
  unique_ptr<float[]> pData(new float[*pWidth * *pHeight]);

  CPLErr result = pRasterBand->RasterIO(GF_Read,       // eRWFlag
                                        0,             // nXOff
                                        0,             // nYOff
                                        *pWidth,       // nXSize
                                        *pHeight,      // nYSize
                                        pData.get(),   // pData
                                        *pWidth,       // nBufXSize
                                        *pHeight,      // nBufYSize
                                        GDT_Float32,   // eBufType
                                        0,             // nPixelSpace
                                        0);            // nLineSpace

  if (result != CE_None) {
    throw runtime_error("failed to read friction raster data");
  }

  return pData;
}


static void
write_cost_layer(const string& filename, GDALDataset *pSource, const float data[], int width, int height)
{
  const char *pszFormat = "GTiff";
  GDALDriver *poDriver;
  char **papszMetadata;

  poDriver = GetGDALDriverManager()->GetDriverByName(pszFormat);
  if (poDriver == NULL) {
    throw runtime_error("cannot retrieve GeoTIFF driver");
  }
  papszMetadata = poDriver->GetMetadata();
  if (!CSLFetchBoolean(papszMetadata, GDAL_DCAP_CREATE, FALSE)) {
    throw runtime_error("driver cannot create new layers");
  }

  GDALDataset *pDataset;
  char **ppOptions = NULL;
  ppOptions = CSLSetNameValue(ppOptions, "COMPRESS", "DEFLATE");
  // ppOptions = CSLSetNameValue(ppOptions, "PREDICTOR", "3");
  pDataset = poDriver->Create(filename.c_str(), width, height, 1, GDT_Float32, ppOptions);
  CSLDestroy(ppOptions);

  double geoTransform[6];
  if (pSource->GetGeoTransform(geoTransform) != CE_None) {
    throw runtime_error("cannot retrieve geotransform of source layer");
  }

  pDataset->SetGeoTransform(geoTransform);
  pDataset->SetProjection(pSource->GetProjectionRef());

  GDALRasterBand *pBand = pDataset->GetRasterBand(1);
  pBand->SetNoDataValue(numeric_limits<float>::infinity());

  CPLErr result = pBand->RasterIO(GF_Write,      // eRWFlag
                                  0,             // nXOff
                                  0,             // nYOff
                                  width,         // nXSize
                                  height,        // nYSize
                                  (void *) data, // pData
                                  width,         // nBufXSize
                                  height,        // nBufYSize
                                  GDT_Float32,   // eBufType
                                  0,             // nPixelSpace
                                  0);            // nLineSpace

  if (result != CE_None) {
    throw runtime_error("failed to write cost raster data");
  }

  GDALClose(pDataset);
}


// ======== Contour algorithm

#define xsect(p1,p2) (h[p2]*xh[p1]-h[p1]*xh[p2])/(h[p2]-h[p1])
#define ysect(p1,p2) (h[p2]*yh[p1]-h[p1]*yh[p2])/(h[p2]-h[p1])

typedef function<void(float,float,float,float,float)> contour_callback_t;

/*
Copyright (c) 1996-1997 Nicholas Yue

This software is copyrighted by Nicholas Yue. This code is base on the work of
Paul D. Bourke CONREC.F routine

The authors hereby grant permission to use, copy, and distribute this
software and its documentation for any purpose, provided that existing
copyright notices are retained in all copies and that this notice is included
verbatim in any distributions. Additionally, the authors grant permission to
modify this software and its documentation for any purpose, provided that
such modifications are not distributed without the explicit consent of the
authors and that existing copyright notices are retained in all copies. Some
of the algorithms implemented by this software are patented, observe all
applicable patent law.

IN NO EVENT SHALL THE AUTHORS OR DISTRIBUTORS BE LIABLE TO ANY PARTY FOR
DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT
OF THE USE OF THIS SOFTWARE, ITS DOCUMENTATION, OR ANY DERIVATIVES THEREOF,
EVEN IF THE AUTHORS HAVE BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

THE AUTHORS AND DISTRIBUTORS SPECIFICALLY DISCLAIM ANY WARRANTIES, INCLUDING,
BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
PARTICULAR PURPOSE, AND NON-INFRINGEMENT.  THIS SOFTWARE IS PROVIDED ON AN
"AS IS" BASIS, AND THE AUTHORS AND DISTRIBUTORS HAVE NO OBLIGATION TO PROVIDE
MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
*/

//=============================================================================
//
//     CONREC is a contouring subroutine for rectangularily spaced data.
//
//     It emits calls to a line drawing subroutine supplied by the user
//     which draws a contour map corresponding to real*4data on a randomly
//     spaced rectangular grid. The coordinates emitted are in the same
//     units given in the x() and y() arrays.
//
//     Any number of contour levels may be specified but they must be
//     in order of increasing value.
//
//     As this code is ported from FORTRAN-77, please be very careful of the
//     various indices like ilb,iub,jlb and jub, remeber that C/C++ indices
//     starts from zero (0)
//
//=============================================================================
int conrec(const float * const *d,
           int ilb,
           int iub,
           int jlb,
           int jub,
           float *x,
           float *y,
           int nc,
           float *z,
           const contour_callback_t& callback)
// d               ! matrix of data to contour
// ilb,iub,jlb,jub ! index bounds of data matrix
// x               ! data matrix column coordinates
// y               ! data matrix row coordinates
// nc              ! number of contour levels
// z               ! contour levels in increasing order
{
  int m1,m2,m3,case_value;
  float dmin,dmax,x1,x2,y1,y2;
  int i,j,k,m;
  float h[5];
  int sh[5];
  float xh[5],yh[5];
  //===========================================================================
  // The indexing of im and jm should be noted as it has to start from zero
  // unlike the fortran counter part
  //===========================================================================
  int im[4] = {0,1,1,0}, jm[4] = {0,0,1,1};
  //===========================================================================
  // Note that castab is arranged differently from the FORTRAN code because
  // Fortran and C/C++ arrays are transposed of each other, in this case
  // it is more tricky as castab is in 3 dimension
  //===========================================================================
  int castab[3][3][3] =
    {
      {
        {0,0,8},{0,2,5},{7,6,9}
      },
      {
        {0,3,4},{1,3,1},{4,3,0}
      },
      {
        {9,6,7},{5,2,0},{8,0,0}
      }
    };
  for (j=(jub-1);j>=jlb;j--) {
    for (i=ilb;i<=iub-1;i++) {
      float temp1,temp2;
      temp1 = min(d[i][j],d[i][j+1]);
      temp2 = min(d[i+1][j],d[i+1][j+1]);
      dmin = min(temp1,temp2);
      temp1 = max(d[i][j],d[i][j+1]);
      temp2 = max(d[i+1][j],d[i+1][j+1]);
      dmax = max(temp1,temp2);
      if (dmax>=z[0]&&dmin<=z[nc-1]) {
        for (k=0;k<nc;k++) {
          if (z[k]>=dmin&&z[k]<=dmax) {
            for (m=4;m>=0;m--) {
              if (m>0) {
                //=============================================================
                // The indexing of im and jm should be noted as it has to
                // start from zero
                //=============================================================
                h[m] = d[i+im[m-1]][j+jm[m-1]]-z[k];
                xh[m] = x[i+im[m-1]];
                yh[m] = y[j+jm[m-1]];
              } else {
                h[0] = 0.25*(h[1]+h[2]+h[3]+h[4]);
                xh[0]=0.5*(x[i]+x[i+1]);
                yh[0]=0.5*(y[j]+y[j+1]);
              }
              if (h[m]>0.0) {
                sh[m] = 1;
              } else if (h[m]<0.0) {
                sh[m] = -1;
              } else
                sh[m] = 0;
            }
            //=================================================================
            //
            // Note: at this stage the relative heights of the corners and the
            // centre are in the h array, and the corresponding coordinates are
            // in the xh and yh arrays. The centre of the box is indexed by 0
            // and the 4 corners by 1 to 4 as shown below.
            // Each triangle is then indexed by the parameter m, and the 3
            // vertices of each triangle are indexed by parameters m1,m2,and
            // m3.
            // It is assumed that the centre of the box is always vertex 2
            // though this isimportant only when all 3 vertices lie exactly on
            // the same contour level, in which case only the side of the box
            // is drawn.
            //
            //
            //      vertex 4 +-------------------+ vertex 3
            //               | \               / |
            //               |   \    m-3    /   |
            //               |     \       /     |
            //               |       \   /       |
            //               |  m=2    X   m=2   |       the centre is vertex 0
            //               |       /   \       |
            //               |     /       \     |
            //               |   /    m=1    \   |
            //               | /               \ |
            //      vertex 1 +-------------------+ vertex 2
            //
            //
            //
            //               Scan each triangle in the box
            //
            //=================================================================
            for (m=1;m<=4;m++) {
              m1 = m;
              m2 = 0;
              if (m!=4)
                m3 = m+1;
              else
                m3 = 1;
              case_value = castab[sh[m1]+1][sh[m2]+1][sh[m3]+1];
              if (case_value!=0) {
                switch (case_value) {
                  //===========================================================
                  //     Case 1 - Line between vertices 1 and 2
                  //===========================================================
                case 1:
                  x1=xh[m1];
                  y1=yh[m1];
                  x2=xh[m2];
                  y2=yh[m2];
                  break;
                  //===========================================================
                  //     Case 2 - Line between vertices 2 and 3
                  //===========================================================
                case 2:
                  x1=xh[m2];
                  y1=yh[m2];
                  x2=xh[m3];
                  y2=yh[m3];
                  break;
                  //===========================================================
                  //     Case 3 - Line between vertices 3 and 1
                  //===========================================================
                case 3:
                  x1=xh[m3];
                  y1=yh[m3];
                  x2=xh[m1];
                  y2=yh[m1];
                  break;
                  //===========================================================
                  //     Case 4 - Line between vertex 1 and side 2-3
                  //===========================================================
                case 4:
                  x1=xh[m1];
                  y1=yh[m1];
                  x2=xsect(m2,m3);
                  y2=ysect(m2,m3);
                  break;
                  //===========================================================
                  //     Case 5 - Line between vertex 2 and side 3-1
                  //===========================================================
                case 5:
                  x1=xh[m2];
                  y1=yh[m2];
                  x2=xsect(m3,m1);
                  y2=ysect(m3,m1);
                  break;
                  //===========================================================
                  //     Case 6 - Line between vertex 3 and side 1-2
                  //===========================================================
                case 6:
                  x1=xh[m3];
                  y1=yh[m3];
                  x2=xsect(m1,m2);
                  y2=ysect(m1,m2);
                  break;
                  //===========================================================
                  //     Case 7 - Line between sides 1-2 and 2-3
                  //===========================================================
                case 7:
                  x1=xsect(m1,m2);
                  y1=ysect(m1,m2);
                  x2=xsect(m2,m3);
                  y2=ysect(m2,m3);
                  break;
                  //===========================================================
                  //     Case 8 - Line between sides 2-3 and 3-1
                  //===========================================================
                case 8:
                  x1=xsect(m2,m3);
                  y1=ysect(m2,m3);
                  x2=xsect(m3,m1);
                  y2=ysect(m3,m1);
                  break;
                  //===========================================================
                  //     Case 9 - Line between sides 3-1 and 1-2
                  //===========================================================
                case 9:
                  x1=xsect(m3,m1);
                  y1=ysect(m3,m1);
                  x2=xsect(m1,m2);
                  y2=ysect(m1,m2);
                  break;
                default:
                  break;
                }
                //=============================================================
                // Put your processing code here and comment out the printf
                //=============================================================
                callback(x1, y1, x2, y2, z[k]);
              }
            }
          }
        }
      }
    }
  }
  return 0;
}

inline bool
coordsEqual(const coords_t& a, const coords_t& b) {
  float df = a.first - b.first;
  float ds = a.second - b.second;
  return df * df + ds * ds < 1e-10;
}

struct contour_builder_t {
  typedef list<coords_t> coords_list_t;

  list<coords_list_t> _sequences;
  int _segments = 0;

  void add_segment(const coords_t& a, const coords_t& b) {
    list<coords_list_t>::iterator itSeqA = _sequences.end();
    list<coords_list_t>::iterator itSeqB = _sequences.end();
    bool prependA, prependB;

    _segments++;
    for (auto seqIt = _sequences.begin(); seqIt != _sequences.end(); seqIt++) {
      if (itSeqA == _sequences.end()) {
        if (coordsEqual(a, seqIt->front())) {
          itSeqA = seqIt;
          prependA = true;
        } else if (coordsEqual(a, seqIt->back())) {
          itSeqA = seqIt;
          prependA = false;
        }
      }
      if (itSeqB == _sequences.end()) {
        if (coordsEqual(b, seqIt->front())) {
          itSeqB = seqIt;
          prependB = true;
        } else if (coordsEqual(b, seqIt->back())) {
          itSeqB = seqIt;
          prependB = false;
        }
      }
      if (itSeqA != _sequences.end() && itSeqB != _sequences.end()) {
        break;
      }
    }

    int c = (itSeqA != _sequences.end() ? 1 : 0) | (itSeqB != _sequences.end() ? 2 : 0);
    switch (c) {
    case 0:
      // new sequence
      itSeqA = _sequences.emplace(itSeqA);
      itSeqA->push_back(a);
      itSeqA->push_back(b);
      break;
    case 1:
      // extend sequence *itSeqA with b
      if (prependA) {
        itSeqA->push_front(b);
      } else {
        itSeqA->push_back(b);
      }
      break;
    case 2:
      // extend sequence *itSeqB with a
      if (prependB) {
        itSeqB->push_front(a);
      } else {
        itSeqB->push_back(a);
      }
      break;
    case 3:
      // join sequences *itSeqA and *itSeqB
      if (itSeqA == itSeqB) {
        // close the loop
        itSeqA->push_back(itSeqA->front());
      } else {
        if (prependA) {
          if (prependB) itSeqB->reverse();
          itSeqA->splice(itSeqA->begin(), *itSeqB);
        } else {
          if (!prependB) itSeqB->reverse();
          itSeqA->splice(itSeqA->end(), *itSeqB);
        }
        _sequences.erase(itSeqB);
      }
      break;
    }
  }

  OGRGeometry *build() {
    cout << "No. of segments " << _segments << endl;
    cout << "No. of sequences " << _sequences.size() << endl;

    // TODO: identify the outer ring and add it first to the polygon

    OGRPolygon *result = new OGRPolygon();
    for (auto seq : _sequences) {
      OGRLinearRing *ring = new OGRLinearRing();
      for (auto point : seq) {
        ring->addPoint(point.first, point.second);
      }
      result->addRing(ring);
    }

    result->closeRings();
    return result;
  }
};

static unique_ptr<OGRGeometry>
extract_isochrone(const float *data, int width, int height, const coords_t& topLeft, const coords_t& bottomRight, float time)
{
#ifdef BENCHMARK
  boost::timer::auto_cpu_timer t(std::cerr, 6, "extract_isochrone: %t sec CPU, %w sec real\n");
#endif

  unique_ptr<const float *[]> dataRows(new const float *[height]);
  const float *p = data;
  for (int i = 0; i < height; i++, p += width) {
    dataRows[i] = p;
  }
  unique_ptr<float[]> latitudes(new float[height]);
  unique_ptr<float[]> longitudes(new float[width]);
  float dLat = (bottomRight.second - topLeft.second) / height;
  float lat = topLeft.second + dLat / 2;
  for (int i = 0; i < height; i++, lat += dLat) {
    latitudes[i] = lat;
  }
  float dLng = (bottomRight.first - topLeft.first) / width;
  float lng = topLeft.first + dLng / 2;
  for (int i = 0; i < width; i++, lng += dLng) {
    longitudes[i] = lng;
  }
  float times[] = { time };

  contour_builder_t builder;
  auto callback = [&builder](float x1, float y1, float x2, float y2, float z) {
    builder.add_segment(make_coords(y1, x1), make_coords(y2, x2));
  };

  conrec(dataRows.get(),
         0, height - 1, 0, width - 1,
         latitudes.get(), longitudes.get(),
         1, times,
         callback);

  return unique_ptr<OGRGeometry>(builder.build());
}

static void
write_isochrone(const string& filename, OGRGeometry *isochrone)
{
  const char *pDriverName = "GeoJSON";
  OGRSFDriver *pDriver = OGRSFDriverRegistrar::GetRegistrar()->GetDriverByName(pDriverName);
  if (pDriver == NULL) {
    throw runtime_error("cannot get GeoJSON driver");
  }

  pDriver->DeleteDataSource(filename.c_str());
  OGRDataSource *pDataSource = pDriver->CreateDataSource(filename.c_str(), NULL);
  if (pDataSource == NULL) {
    throw runtime_error("cannot create output file");
  }

  OGRLayer *pLayer = pDataSource->CreateLayer("test", isochrone->getSpatialReference(), wkbMultiLineString, NULL);
  if (pLayer == NULL) {
    throw runtime_error("cannot create layer");
  }

  OGRFieldDefn nameField("Name", OFTString);
  nameField.SetWidth(64);
  if (pLayer->CreateField(&nameField) != OGRERR_NONE) {
    throw runtime_error("cannot create field");
  }

  OGRFeature *pFeature;
  pFeature = OGRFeature::CreateFeature(pLayer->GetLayerDefn());
  pFeature->SetField("Name", "isochrone");
  pFeature->SetGeometry(isochrone);

  if (pLayer->CreateFeature(pFeature) != OGRERR_NONE) {
    throw runtime_error("cannot write isochrone feature");
  }
  OGRFeature::DestroyFeature(pFeature);

  OGRDataSource::DestroyDataSource(pDataSource);
}


// ======== Main entry point

// program exit codes
namespace {
  const size_t SUCCESS = 0;
  const size_t ERROR_IN_COMMAND_LINE = 1;
  const size_t ERROR_OTHER = 2;
}

int main(int argc, char *argv[])
{
  run_options_t options;

  if (!parse_command_line(argc, argv, options)) {
    return ERROR_IN_COMMAND_LINE;
  }
  if (options._rasterPath.empty()) {
    // eg. if help was requested
    return SUCCESS;
  }

  GDALAllRegister();
  OGRRegisterAll();

  GDALDataset *poDataset;

  poDataset = (GDALDataset *) GDALOpen(options._rasterPath.c_str(), GA_ReadOnly);
  if (poDataset == NULL) {
    return ERROR_OTHER;
  }

  raster_t frictionRaster(poDataset);
  if (!frictionRaster.is_north_up()) {
    cerr << "ERROR: raster must be normalized 'north-up'" << endl;
    return ERROR_OTHER;
  }

  cout << "Using friction raster file: " << options._rasterPath << endl;

  show_raster_info(frictionRaster);

  cout << "Using origin at " << options._origin << endl;

  if (frictionRaster.contains(options._origin)) {
    cout << "OK" << endl;
  } else {
    cerr << "ERROR: origin out of raster boundaries" << endl;
    return ERROR_OTHER;
  }

  pixel_coords_t pixelOrigin(frictionRaster.pixel_coords(options._origin));
  cout << "Pixel origin at " << pixelOrigin << endl;

  int width, height;
  unique_ptr<float[]> friction = load_friction_data(frictionRaster.dataset(), 1, &width, &height);

  const int maxTimeCost = options._maxTimeCost; // minutes
  unique_ptr<float[]> cost =
    run_dijkstra_on_friction_layer(friction.get(),
                                   width, height,
                                   frictionRaster.pixel_width_meters(),
                                   frictionRaster.pixel_height_meters(),
                                   pixelOrigin.first, pixelOrigin.second,
                                   maxTimeCost,
                                   options._minFriction);

  if (!options._outputCostPath.empty()) {
    write_cost_layer(options._outputCostPath, frictionRaster.dataset(), cost.get(), width, height);
    cout << "Wrote " << options._outputCostPath << endl;
  }

  if (!options._outputIsochronePath.empty()) {
    unique_ptr<OGRGeometry> isochrone = extract_isochrone(cost.get(), width, height, frictionRaster.top_left_coords(), frictionRaster.bottom_right_coords(), maxTimeCost);

    OGRSpatialReference *spatialRef = new OGRSpatialReference(frictionRaster.dataset()->GetProjectionRef());
    isochrone->assignSpatialReference(spatialRef);

    write_isochrone(options._outputIsochronePath, isochrone.get());
    cout << "Wrote " << options._outputIsochronePath << endl;
  }

  return SUCCESS;
}
