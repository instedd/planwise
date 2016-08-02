#include <iostream>
#include <string>
#include <math.h>
#include <stdio.h>
#include <tuple>

#include "gdal_priv.h"
#include "cpl_conv.h"
#include "cpl_string.h"

typedef unsigned char BYTE;

#define DEBUG 1

GDALDataset* openRaster(std::string filename) {
  GDALDataset* poDataset = (GDALDataset*) GDALOpen(filename.c_str(), GA_ReadOnly);
  if (poDataset == NULL) {
    std::cerr << "Failed opening: " << filename << std::endl;
    exit(1);
  }
  return poDataset;
};

GDALDataset* copyRaster(GDALDataset* sourceRaster, std::string filename, int blockXSize, int blockYSize) {
  const char *pszFormat = "GTiff";
  GDALDriver *poDriver;
  poDriver = GetGDALDriverManager()->GetDriverByName(pszFormat);
  if (poDriver == NULL) {
    std::cerr << "Failed loading driver" << std::endl;
    exit(1);
  }

  char **papszOptions = NULL;
  papszOptions = CSLSetNameValue(papszOptions, "TILED", "YES");
  papszOptions = CSLSetNameValue(papszOptions, "BLOCKXSIZE", std::to_string(blockXSize).c_str());
  papszOptions = CSLSetNameValue(papszOptions, "BLOCKYSIZE", std::to_string(blockYSize).c_str());

  GDALDataset* dataset = poDriver->CreateCopy(filename.c_str(), sourceRaster, FALSE, papszOptions, NULL, NULL);
  dataset->FlushCache();
  return dataset;
}

void closeRaster(GDALDataset* rasterDataSet) {
  GDALClose(rasterDataSet);
};

long calculateDemand(std::string targetFilename, std::string demoFilename, float capacity, std::vector<std::string> facilities) {
  GDALDataset* demoDataset = openRaster(demoFilename);
  GDALRasterBand* demoBand = demoDataset->GetRasterBand(1);
  CPLAssert(demoBand->GetRasterDataType() == GDT_Float32);

  int xBlockSize, yBlockSize;
  demoBand->GetBlockSize(&xBlockSize, &yBlockSize);

  GDALDataset* targetDataset = copyRaster(demoDataset, targetFilename, xBlockSize, yBlockSize);
  GDALRasterBand* targetBand = targetDataset->GetRasterBand(1);
  closeRaster(demoDataset);

  targetBand->GetBlockSize(&xBlockSize, &yBlockSize);
  int xSize = targetDataset->GetRasterXSize();
  int ySize = targetDataset->GetRasterYSize();
  int nXBlocks = (xSize + xBlockSize - 1)/xBlockSize;
  int nYBlocks = (ySize + yBlockSize - 1)/yBlockSize;
  int nXValid, nYValid, xOffset, yOffset;

#ifdef DEBUG
  std::cerr << "Target raster properties:" << std::endl;
  std::cerr << " xSize " << xSize << std::endl;
  std::cerr << " ySize " << ySize << std::endl;
  std::cerr << " xBlockSize " << xBlockSize << std::endl;
  std::cerr << " yBlockSize " << yBlockSize << std::endl;
  std::cerr << " nYBlocks " << nYBlocks << std::endl;
  std::cerr << " nXBlocks " << nXBlocks << std::endl;
#endif

  float* buffer = (float*) CPLMalloc(sizeof(float)*xBlockSize*yBlockSize);
  BYTE* facilityBuffer = (BYTE*) CPLMalloc(sizeof(BYTE)*xBlockSize*yBlockSize);

  float nodata = targetBand->GetNoDataValue();

  for (std::vector<std::string>::iterator it = facilities.begin(); it != facilities.end(); ++it) {
#ifdef DEBUG
    std::cerr << "Processing facility " << (*it) << std::endl;
#endif
    GDALDataset* facilityDataset = openRaster(*it);
    GDALRasterBand* facilityBand = facilityDataset->GetRasterBand(1);
    BYTE facilityNodata = facilityBand->GetNoDataValue();

    CPLAssert(facilityBand->GetRasterDataType() == GDT_Byte);
    int xBlockSizeFacility, yBlockSizeFacility;
    facilityBand->GetBlockSize(&xBlockSizeFacility, &yBlockSizeFacility);
    CPLAssert(xBlockSizeFacility == xBlockSize);
    CPLAssert(yBlockSizeFacility == yBlockSize);
    CPLAssert(facilityDataset->GetRasterXSize() == xSize);
    CPLAssert(facilityDataset->GetRasterYSize() == ySize);

    // First pass: we count the still unsatisfied population under the isochrone
    float unsatisfiedCount = 0.0f;
    for (int iXBlock = 0; iXBlock < nXBlocks; ++iXBlock) {
      xOffset = iXBlock*xBlockSize;
      nXValid = xBlockSize;
      if (iXBlock == nXBlocks-1) nXValid = xSize - xOffset;

      for (int iYBlock = 0; iYBlock < nYBlocks; ++iYBlock) {
        yOffset = iYBlock*yBlockSize;
        nYValid = yBlockSize;
        if (iYBlock == nYBlocks-1) nYValid = ySize - yOffset;

        targetBand->ReadBlock(iXBlock, iYBlock, buffer);
        facilityBand->ReadBlock(iXBlock, iYBlock, facilityBuffer);

        for (int iY = 0; iY < nYValid; ++iY) {
          for (int iX = 0; iX < nXValid; ++iX) {
            int iBuff = xBlockSize*iY+iX;
            if (buffer[iBuff] != nodata && facilityBuffer[iBuff] != facilityNodata) {
              unsatisfiedCount += buffer[iBuff];
            }
          }
        }
      }
    }

    // TODO: Unhardcode capacity
    // TODO: The actual capacity considered should be proportional to the
    // area of the isochrone that lands on this region
    // float capacity = 5000.0;

    // Each pixel under the isochrone will be multiplied by the proportion of
    // the unsatisfied demand that is satisfied by this facility.
    float factor = 1 - (capacity / unsatisfiedCount);
    if (factor < 0) factor = 0;

#ifdef DEBUG
    std::cerr << " Capacity is " << capacity << std::endl;
    std::cerr << " Unsatisfied count is " << unsatisfiedCount << std::endl;
    std::cerr << " Satisfaction factor is " << factor << std::endl;
#endif

    // Second pass: we apply the multiplying factor to each pixel
    for (int iXBlock = 0; iXBlock < nXBlocks; ++iXBlock) {
      xOffset = iXBlock*xBlockSize;
      nXValid = xBlockSize;
      if (iXBlock == nXBlocks-1) nXValid = xSize - xOffset;

      for (int iYBlock = 0; iYBlock < nYBlocks; ++iYBlock) {
        yOffset = iYBlock*yBlockSize;
        nYValid = yBlockSize;
        if (iYBlock == nYBlocks-1) nYValid = ySize - yOffset;

        targetBand->ReadBlock(iXBlock, iYBlock, buffer);
        facilityBand->ReadBlock(iXBlock, iYBlock, facilityBuffer);

        bool blockChanged = false;

        for (int iY = 0; iY < nYValid; ++iY) {
          for (int iX = 0; iX < nXValid; ++iX) {
            int iBuff = xBlockSize*iY+iX;
            if (buffer[iBuff] != nodata && facilityBuffer[iBuff] != facilityNodata) {
              blockChanged = true;
              buffer[iBuff] *= factor;
            }
          }
        }

        if (blockChanged) {
          targetBand->WriteBlock(iXBlock, iYBlock, buffer);
        }
      }
    }

    closeRaster(facilityDataset);
  }

  targetDataset->FlushCache();

  // Finally, make a last pass on the target dataset to count the total
  // unsatisfied population and return it
  float totalUnsatisfied = 0.0;
  for (int iXBlock = 0; iXBlock < nXBlocks; ++iXBlock) {
    xOffset = iXBlock*xBlockSize;
    nXValid = xBlockSize;
    if (iXBlock == nXBlocks-1) nXValid = xSize - xOffset;

    for (int iYBlock = 0; iYBlock < nYBlocks; ++iYBlock) {
      yOffset = iYBlock*yBlockSize;
      nYValid = yBlockSize;
      if (iYBlock == nYBlocks-1) nYValid = ySize - yOffset;

      targetBand->ReadBlock(iXBlock, iYBlock, buffer);

      for (int iY = 0; iY < nYValid; ++iY) {
        for (int iX = 0; iX < nXValid; ++iX) {
          int iBuff = xBlockSize*iY+iX;
          if (buffer[iBuff] != nodata) {
            totalUnsatisfied += buffer[iBuff];
          }
        }
      }
    }
  }

  closeRaster(targetDataset);

  return totalUnsatisfied;
}

int main(int argc, char *argv[]) {
  GDALAllRegister();

  if (argc < 5) {
    std::cerr << "Usage: " << argv[0] << " TARGET.tif POPULATION.tif DEFAULTCAPACITY FACILITYMASK1.tif ... FACILITYMASKN.tif";
    exit(1);
  }

  std::vector<std::string> facilities;
  for (int i = 4; i < argc; i++) {
    facilities.push_back(argv[i]);
  }

  long unsatisifiedDemand = calculateDemand(argv[1], argv[2], atof(argv[3]), facilities);
  std::cout << unsatisifiedDemand << std::endl;;
}
