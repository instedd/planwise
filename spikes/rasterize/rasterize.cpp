#include <iostream>
#include <string>
#include <vector>
#include <fstream>
#include <limits>
#include "gdal.h"
#include "gdal_alg.h"
#include "gdal_priv.h"
#include "cpl_string.h"
#include "ogr_api.h"
#include "ogr_srs_api.h"

struct GeoReferenceExtent {
  double minX;
  double minY;
  double maxX;
  double maxY;
};

struct RasterConfig {
  int burnValue;
  int xPixelsSize;
  int yPixelsSize;
  int xBlockSize;
  int yBlockSize;
};

std::vector<OGRGeometryH> extractGeometries(OGRLayerH layer) {
  OGRFeatureH hFeat;
  std::vector<OGRGeometryH> ahGeometries;

  OGR_L_ResetReading( layer );
  std::vector<double> adfBurnValues(1, 255);
  hFeat = OGR_L_GetNextFeature( layer );
  OGRGeometryH hGeom;
  if( OGR_F_GetGeometryRef( hFeat ) == NULL )
  {
    OGR_F_Destroy( hFeat );
  }
  hGeom = OGR_G_Clone( OGR_F_GetGeometryRef( hFeat ) );
  ahGeometries.push_back( hGeom );
  OGR_F_Destroy( hFeat );

  return ahGeometries;
};

void tearDown(OGRDataSourceH vectorDataSet, GDALDatasetH rasterDataSet) {
  OGR_DS_Destroy( vectorDataSet );
  GDALClose( rasterDataSet );
  // OSRDestroySpatialReference(hSRS); // TODO: this line is needed and is throwing SEGFAULT
  // CSLDestroy( papszRasterizeOptions );
  GDALDestroyDriverManager();
  OGRCleanupAll();
};

GDALDatasetH createRaster(OGRLayerH layer, RasterConfig rasterConfig, GeoReferenceExtent geoReferenceExtent, const char* outFilename) {
    double dfXRes = (geoReferenceExtent.maxX-geoReferenceExtent.minX) / rasterConfig.xPixelsSize;
    double dfYRes = (geoReferenceExtent.maxY-geoReferenceExtent.minY) / rasterConfig.yPixelsSize;
    GDALDataType eOutputType = GDT_Float32;
    int bandCount = 1;
    char** papszCreateOptions = NULL;
    papszCreateOptions = CSLSetNameValue(papszCreateOptions, "TILED", "YES");
    papszCreateOptions = CSLSetNameValue(papszCreateOptions, "BLOCKXSIZE", "128");
    papszCreateOptions = CSLSetNameValue(papszCreateOptions, "BLOCKYSIZE", "128");
    GDALDriverH hDriver = GDALGetDriverByName("GTiff");
    GDALDatasetH hDstDS = GDALCreate(hDriver, outFilename, rasterConfig.xPixelsSize, rasterConfig.yPixelsSize,
				     bandCount, eOutputType, papszCreateOptions);
    if (hDstDS == NULL)
    {
      fprintf(stderr, "Cannot create %s\n", outFilename);
        exit(2);
    }

    double adfProjection[6];
    adfProjection[0] = geoReferenceExtent.minX;
    adfProjection[1] = dfXRes;
    adfProjection[2] = 0;
    adfProjection[3] = geoReferenceExtent.maxY;
    adfProjection[4] = 0;
    adfProjection[5] = -dfYRes;
    GDALSetGeoTransform(hDstDS, adfProjection);

    char* pszWKT = NULL;
    OGRSpatialReferenceH hSRS = OGR_L_GetSpatialRef(layer);
    OSRExportToWkt(hSRS, &pszWKT);
    GDALSetProjection(hDstDS, pszWKT);
    CPLFree(pszWKT);

    return hDstDS;
}

OGRDataSourceH openOGR(const char* inFilename) {
  OGRDataSourceH hSrcDS = OGROpen(inFilename, FALSE, NULL);
  if( hSrcDS == NULL )
  {
    fprintf( stderr, "Failed to open feature source: %s\n",
             inFilename);
    exit( 1 );
  }

  return hSrcDS;
}

void setup() {
  GDALAllRegister();
  OGRRegisterAll();
}

void rasterize(const char* inFilename, const char* outFilename, RasterConfig& rasterConfig, GeoReferenceExtent& geoReferenceExtent) {
  setup();
  OGRDataSourceH vectorDataSet = openOGR(inFilename);
  OGRLayerH layer = OGR_DS_GetLayerByName(vectorDataSet, OGR_L_GetName(OGR_DS_GetLayer(vectorDataSet, 0)));
  GDALDatasetH rasterDataSet = createRaster(layer, rasterConfig, geoReferenceExtent, outFilename);

  std::vector<OGRGeometryH> sourceGeometries = extractGeometries(layer);
  std::vector<int> anBandList(1, 1);
  std::vector<double> adfFullBurnValues(1, rasterConfig.burnValue);
  GDALRasterizeGeometries(rasterDataSet, anBandList.size(), &(anBandList[0]),
                          sourceGeometries.size(), &(sourceGeometries[0]),
                          NULL, NULL, &(adfFullBurnValues[0]),
                          NULL,
                          NULL, NULL );


  int iGeom;
  for( iGeom = sourceGeometries.size()-1; iGeom >= 0; iGeom-- )
    OGR_G_DestroyGeometry( sourceGeometries[iGeom] );

  tearDown(vectorDataSet, rasterDataSet);
}

int main()
{
  const char* inFilename = "inout/polygon.geojson";
  const char* outFilename = "inout/polygon.tiff";

  RasterConfig config;
  config.burnValue = 255;
  config.xPixelsSize = 9601;
  config.yPixelsSize = 12179;
  config.xBlockSize = 128;
  config.yBlockSize = 128;

  GeoReferenceExtent outputExtent;
  outputExtent.minX = 33.9126084;
  outputExtent.minY = -4.6780661;
  outputExtent.maxX = 41.9131217;
  outputExtent.maxY = 5.4706946;

  rasterize(inFilename, outFilename, config, outputExtent);

  return 0;
}
