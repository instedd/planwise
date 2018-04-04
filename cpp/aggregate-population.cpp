#include <iostream>
#include <string>
#include <math.h>
#include <cassert>
#include <stdio.h>

#include "gdal_priv.h"
#include "cpl_conv.h"

#define UNUSED(x) (void)(x)

GDALDataset* openRaster(std::string filename) {
  GDALDataset* poDataset = (GDALDataset*) GDALOpen(filename.c_str(), GA_ReadOnly);
  if (poDataset == NULL) {
    std::cout << "Failed opening: " << filename << std::endl;
    exit(1);
  }
  return poDataset;
};

void closeRaster(GDALDataset* rasterDataSet) {
  GDALClose(rasterDataSet);
};

int main(int argc, char *argv[]) {
  GDALAllRegister();

  if (argc < 2) {
    std::cout << "Usage: aggregate-population RASTERFILE" << std::endl;
    exit(1);
  }

  GDALDataset* dataset = openRaster(argv[1]);
  GDALRasterBand* band = dataset->GetRasterBand(1);

  CPLAssert(band->GetRasterDataType() == GDT_Float32);

  int xBlockSize, yBlockSize;
  band->GetBlockSize(&xBlockSize, &yBlockSize);

  int xSize = dataset->GetRasterXSize();
  int ySize = dataset->GetRasterYSize();
  int nXBlocks = (xSize + xBlockSize - 1)/xBlockSize;
  int nYBlocks = (ySize + yBlockSize - 1)/yBlockSize;
  int nXValid, nYValid, xOffset, yOffset;

  float* buffer = (float*) CPLMalloc(sizeof(float)*xBlockSize*yBlockSize);
  float nodata = band->GetNoDataValue();
  float max = 0;
  double sum = 0;

  CPLErr err;
  UNUSED(err);  // error checking disabled in release build

  for (int iYBlock = 0; iYBlock < nYBlocks; ++iYBlock) {
    yOffset = iYBlock*yBlockSize;
    nYValid = yBlockSize;
    if (iYBlock == nYBlocks-1) nYValid = ySize - yOffset;

    for (int iXBlock = 0; iXBlock < nXBlocks; ++iXBlock) {
      xOffset = iXBlock*xBlockSize;
      nXValid = xBlockSize;
      if (iXBlock == nXBlocks-1) nXValid = xSize - xOffset;

      err = band->ReadBlock(iXBlock, iYBlock, buffer);
      assert(err == CE_None);

      for (int iY = 0; iY < nYValid; ++iY) {
        for (int iX = 0; iX < nXValid; ++iX) {
          int iBuff = xBlockSize*iY+iX;
          if (buffer[iBuff] != nodata) {
            sum += buffer[iBuff];
            if (buffer[iBuff] > max) {
              max = buffer[iBuff];
            }
          }
        }
      }
    }
  }

  std::cout << ((long)sum) << " " << ((long)ceil(max)) << std::endl;
}
