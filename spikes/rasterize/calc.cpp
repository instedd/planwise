#include <iostream>
#include <string>
#include <math.h>
#include "gdal_priv.h"
#include "cpl_conv.h"

GDALDataset* openRaster(std::string filename) {
  GDALDataset* poDataset = (GDALDataset *) GDALOpen(filename.c_str(), GA_ReadOnly);
  if( poDataset == NULL )
  {
    std::cout << "Failed opening: " << filename << std::endl;
    exit(1);
  }

  return poDataset;
};

void closeRaster(GDALDataset* rasterDataSet) {
  GDALClose(rasterDataSet);
};

GDALDataset* createRasterFrom(GDALDataset* referenceRaster, const char* outFilename) {
  int bandCount = 1;
  char** papszCreateOptions = NULL;
  papszCreateOptions = CSLSetNameValue(papszCreateOptions, "TILED", "YES");
  papszCreateOptions = CSLSetNameValue(papszCreateOptions, "BLOCKXSIZE", "128");
  papszCreateOptions = CSLSetNameValue(papszCreateOptions, "BLOCKYSIZE", "128");
  GDALDriver* hDriver = (GDALDriver*)GDALGetDriverByName("GTiff");
  int xPixelsSize = referenceRaster->GetRasterXSize();
  int yPixelsSize = referenceRaster->GetRasterYSize();
  GDALDataType eOutputType = GDT_Float32;
  GDALDataset* hDstDS = hDriver->Create(outFilename, xPixelsSize, yPixelsSize,
            bandCount, eOutputType, papszCreateOptions);
  if (hDstDS == NULL)
  {
    fprintf(stderr, "Cannot create %s\n", outFilename);
      exit(2);
  }

  double adfProjection[6];
  referenceRaster->GetGeoTransform(adfProjection);
  hDstDS->SetGeoTransform(adfProjection);

  const char* referenceProjection  = referenceRaster->GetProjectionRef();
  hDstDS->SetProjection(referenceProjection);

  return hDstDS;
};

void filter(GDALDataset* base, GDALDataset* filter, GDALDataset* filtered) {
  GDALRasterBand* baseBand = base->GetRasterBand(1);
  GDALRasterBand* filterBand = filter->GetRasterBand(1);
  GDALRasterBand* filteredBand = filtered->GetRasterBand(1);

  int xBlockSize, yBlockSize;
  CPLAssert( baseBand->GetRasterDataType() == GDT_Float32 );
  CPLAssert( sizeof(float) == 4 );
  baseBand->GetBlockSize(&xBlockSize, &yBlockSize);
  baseBand->GetBlockSize(&xBlockSize, &yBlockSize);
  int xSize = base->GetRasterXSize();
  int ySize = base->GetRasterYSize();
  int nXBlocks = (xSize + xBlockSize - 1)/xBlockSize;
  int nYBlocks = (ySize + yBlockSize - 1)/yBlockSize;

  float* baseBuff = (float*) CPLMalloc(sizeof(float)*xBlockSize*yBlockSize);
  float* filterBuff = (float*) CPLMalloc(sizeof(float)*xBlockSize*yBlockSize);
  float* filteredBuff = (float*) CPLMalloc(sizeof(float)*xBlockSize*yBlockSize);
  int nXValid, nYValid, xOffset, yOffset;
  for (int iXBlock = 0; iXBlock < nXBlocks; ++iXBlock) {
    xOffset = iXBlock*xBlockSize;
    nXValid = xBlockSize;
    if (iXBlock == nXBlocks-1) nXValid = xSize - xOffset;

    for (int iYBlock = 0; iYBlock < nYBlocks; ++iYBlock) {
      yOffset = iYBlock*yBlockSize;
      nYValid = yBlockSize;
      if (iYBlock == nYBlocks-1) nYValid = ySize - yOffset;

      baseBand->ReadBlock(iXBlock, iYBlock, baseBuff);
      filterBand->ReadBlock(iXBlock, iYBlock, filterBuff);

      for (int iY = 0; iY < nYValid; ++iY) {
        for (int iX = 0; iX < nXValid; ++iX) {
          int iBuff = xBlockSize*iY+iX;
          if (fabs(filterBuff[iBuff] - 255) < 1) {
            filteredBuff[iBuff] = baseBuff[iBuff];
          } else {
            filteredBuff[iBuff] = 0; // NoDataValue
          }
        }
      }

      filteredBand->WriteBlock(iXBlock, iYBlock, filteredBuff);
    }
  }

  CPLFree(baseBuff);
  CPLFree(filterBuff);
  CPLFree(filteredBuff);
};

int main() {
  GDALAllRegister();

  std::string isochroneFilename("inout/polygon.tiff");
  std::string kenyaPoluationFilename("inout/KEN_popmap15_v2b.tif");
  std::string outFilename("inout/filtered_population.tiff");

  GDALDataset* isochrone = openRaster(isochroneFilename);
  GDALDataset* kenyaPopulation = openRaster(kenyaPoluationFilename);
  GDALDataset* blankRaster = createRasterFrom(kenyaPopulation, outFilename.c_str());

  filter(kenyaPopulation, isochrone, blankRaster);
  closeRaster(isochrone);
  closeRaster(kenyaPopulation);
  closeRaster(blankRaster);

  return 0;
}
