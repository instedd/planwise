#include "boost/program_options.hpp"
#include "boost/filesystem.hpp"
#include "boost/algorithm/string.hpp"
#include "boost/heap/binomial_heap.hpp"
#include "boost/timer/timer.hpp"

#include "gdal_priv.h"
#include "cpl_conv.h"

#include <iostream>
#include <string>
#include <sstream>
#include <stdexcept>

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

  if (options._outputCostPath.empty()) {
    // nothing to do
    cout << "Nothing to do" << endl;
    return SUCCESS;
  }

  const int maxTimeCost = options._maxTimeCost; // minutes
  unique_ptr<float[]> cost =
    run_dijkstra_on_friction_layer(friction.get(),
                                   width, height,
                                   frictionRaster.pixel_width_meters(),
                                   frictionRaster.pixel_height_meters(),
                                   pixelOrigin.first, pixelOrigin.second,
                                   maxTimeCost,
                                   options._minFriction);

  write_cost_layer(options._outputCostPath, frictionRaster.dataset(), cost.get(), width, height);
  cout << "Wrote " << options._outputCostPath << endl;

  return SUCCESS;
}
