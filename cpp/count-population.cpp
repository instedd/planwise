#include <iostream>
#include <string>
#include <math.h>
#include <stdio.h>

#include "gdal_priv.h"
#include "cpl_conv.h"

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

int main() {
  GDALAllRegister();

  GDALDataset* dataset = openRaster("../mapserver/data/KEN_popmap15_v2b.tif");

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
  float acum = 0;
  float nodata = band->GetNoDataValue();

  for (int iXBlock = 0; iXBlock < nXBlocks; ++iXBlock) {
    xOffset = iXBlock*xBlockSize;
    nXValid = xBlockSize;
    if (iXBlock == nXBlocks-1) nXValid = xSize - xOffset;

    for (int iYBlock = 0; iYBlock < nYBlocks; ++iYBlock) {
      yOffset = iYBlock*yBlockSize;
      nYValid = yBlockSize;
      if (iYBlock == nYBlocks-1) nYValid = ySize - yOffset;

      band->ReadBlock(iXBlock, iYBlock, buffer);

      for (int iY = 0; iY < nYValid; ++iY) {
        for (int iX = 0; iX < nXValid; ++iX) {
          int iBuff = xBlockSize*iY+iX;
          if (buffer[iBuff] != nodata) {
            acum += buffer[iBuff];
          }
        }
      }
    }
  }

  std::cout << std::fixed << acum << std::endl;
}
