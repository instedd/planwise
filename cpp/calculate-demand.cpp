#include <iostream>
#include <sstream>
#include <fstream>
#include <string>
#include <vector>
#include <cmath>
#include <cassert>
#include <stdio.h>
#include <sys/stat.h>

#include "gdal_priv.h"
#include "cpl_conv.h"
#include "cpl_string.h"

// #define BENCHMARK 1
// #define DEBUG 1

#ifdef BENCHMARK
#include <boost/timer/timer.hpp>
#endif

typedef unsigned char BYTE;

const char* DEMAND_METADATA_KEY = "UNSATISFIED_DEMAND";
const char* APP_METADATA_DOMAIN = "PLANWISE";

// See http://stackoverflow.com/a/13636164/12791
template <typename T> std::string numberToString (T number) {
  std::ostringstream stringStream;
  stringStream << number;
  return stringStream.str();
}

bool fileExists (const std::string& name) {
  struct stat buf;
  return (stat(name.c_str(), &buf) == 0);
}

GDALDataset* openRaster(std::string filename) {
  GDALDataset* poDataset = (GDALDataset*) GDALOpen(filename.c_str(), GA_ReadOnly);
  if (poDataset == NULL) {
    std::cerr << "Failed opening: " << filename << std::endl;
    exit(1);
  }
  return poDataset;
};

void closeRaster(GDALDataset* rasterDataSet) {
  GDALClose(rasterDataSet);
};

long readUnsatisfiedDemand(std::string targetFilename) {
  GDALDataset* targetDataset = openRaster(targetFilename);
  const char* metadataValue = targetDataset->GetMetadataItem(DEMAND_METADATA_KEY, APP_METADATA_DOMAIN);
  if (metadataValue == NULL) {
    std::cerr << "No unsatisfied demand metadata found on " << APP_METADATA_DOMAIN << ":" << DEMAND_METADATA_KEY << std::endl;
    exit(1);
  }
  closeRaster(targetDataset);
  return atol(metadataValue);
}

long calculateUnsatisfiedDemand(std::string targetFilename, std::string demoFilename, float saturation, std::vector<std::string> facilities, std::vector<float> capacities) {

#ifdef BENCHMARK
  boost::timer::auto_cpu_timer t(std::cerr, 6, "Total time elapsed: %t sec CPU, %w sec real\n");
#endif

  GDALDataset* targetDataset = NULL;
  int xBlockSize, yBlockSize, targetXSize, targetYSize, targetNXBlocks, targetNYBlocks;
  int nXValid, nYValid, xOffset, yOffset, dataOffset;
  float* buffer;
  float* data;
  double demoProjection[6];
  double facilityProjection[6];
  float nodata;
  std::string demoProjectionWKT;

  /****************************************************************************
   * Read demographics raster into memory buffer
   ****************************************************************************/
  {
#ifdef BENCHMARK
    boost::timer::auto_cpu_timer t(std::cerr, 6, "Read demographics raster: %t sec CPU, %w sec real\n");
#endif
    GDALDataset* demoDataset = openRaster(demoFilename);
    GDALRasterBand* demoBand = demoDataset->GetRasterBand(1);
    assert(demoBand->GetRasterDataType() == GDT_Float32);
    demoBand->GetBlockSize(&xBlockSize, &yBlockSize);
    demoDataset->GetGeoTransform(demoProjection);

    targetXSize = demoDataset->GetRasterXSize();
    targetYSize = demoDataset->GetRasterYSize();
    targetNXBlocks = (targetXSize + xBlockSize - 1)/xBlockSize;
    targetNYBlocks = (targetYSize + yBlockSize - 1)/yBlockSize;
    nodata = demoBand->GetNoDataValue();
    demoProjectionWKT = demoDataset->GetProjectionRef();
    data = (float*) CPLMalloc(sizeof(float) * (xBlockSize * yBlockSize) * (targetNXBlocks * targetNYBlocks));

    for (int iYBlock = 0; iYBlock < targetNYBlocks; ++iYBlock) {
      for (int iXBlock = 0; iXBlock < targetNXBlocks; ++iXBlock) {
        dataOffset = (targetNXBlocks * iYBlock + iXBlock) * (xBlockSize * yBlockSize);
        CPLErr err = demoBand->ReadBlock(iXBlock, iYBlock, data + dataOffset);
        assert(err == CE_None);
      }
    }

    closeRaster(demoDataset);
  }


#ifdef DEBUG
  std::cerr << "Target raster properties:" << std::endl;
  std::cerr << " xSize " << targetXSize << std::endl;
  std::cerr << " ySize " << targetYSize << std::endl;
  std::cerr << " xBlockSize " << xBlockSize << std::endl;
  std::cerr << " yBlockSize " << yBlockSize << std::endl;
  std::cerr << " nYBlocks " << targetNYBlocks << std::endl;
  std::cerr << " nXBlocks " << targetNXBlocks << std::endl;
#endif

  /****************************************************************************
   * Iterate over all facilities and substract from unsatisfied demand
   ****************************************************************************/

  BYTE* facilityBuffer = (BYTE*) CPLMalloc(sizeof(BYTE)*xBlockSize*yBlockSize);
  for (size_t iFacility = 0; iFacility < facilities.size(); iFacility++) {

#ifdef DEBUG
    std::cerr << "Processing facility " << facilities[iFacility] << std::endl;
#endif

#ifdef BENCHMARK
    boost::timer::auto_cpu_timer t(std::cerr, 6, "Process facility: %t sec CPU, %w sec real\n");
#endif

    // open isochrone dataset
    GDALDataset* facilityDataset = openRaster(facilities[iFacility]);
    GDALRasterBand* facilityBand = facilityDataset->GetRasterBand(1);
    BYTE facilityNodata = facilityBand->GetNoDataValue();
    assert(facilityBand->GetRasterDataType() == GDT_Byte);

    // set blocks offsets
    facilityDataset->GetGeoTransform(facilityProjection);

    double epsilon = 0.01;
    double facilityMaxY = facilityProjection[3];
    double demoMaxY = demoProjection[3];
    double facilityYRes = facilityProjection[5];
    double demoYRes = demoProjection[5];
    assert(std::abs(facilityYRes - demoYRes) < epsilon);
    assert(facilityMaxY <= (demoMaxY + epsilon));
    double blocksY = (facilityMaxY - demoMaxY)/(128 * demoYRes);
    int blocksYOffset = round(blocksY);
    assert(std::abs(blocksY - blocksYOffset) < epsilon);

    double facilityMinX = facilityProjection[0];
    double demoMinX = demoProjection[0];
    double facilityXRes = facilityProjection[1];
    double demoXRes = demoProjection[1];
    assert(std::abs(facilityXRes - demoXRes) < epsilon);
    assert(demoMinX <= (facilityMinX + epsilon));
    double blocksX = (facilityMinX - demoMinX)/(128 * demoXRes);
    int blocksXOffset = round(blocksX);
    assert(std::abs(blocksX - blocksXOffset) < epsilon);

    int facilityXSize = facilityDataset->GetRasterXSize();
    int facilityYSize = facilityDataset->GetRasterYSize();
    int facilityNXBlocks = (facilityXSize + xBlockSize - 1)/xBlockSize;
    int facilityNYBlocks = (facilityYSize + yBlockSize - 1)/yBlockSize;

    assert(facilityXSize <= (targetXSize - (xBlockSize * blocksXOffset)));
    assert(facilityYSize <= (targetYSize - (yBlockSize * blocksYOffset)));

#ifdef DEBUG
    std::cerr << " Facility:     " << "maxY=" << facilityMaxY << " minX=" << facilityMinX << std::endl;
    std::cerr << " Demographics: " << "maxY=" << demoMaxY << " minX=" << demoMinX << std::endl;
    std::cerr << " Blocks offsets: " << "X=" << blocksXOffset << " Y=" << blocksYOffset << std::endl;
#endif

    /****************************************************************************
     * First pass to count the still unsatisfied population under the isochrone
     ****************************************************************************/
    double unsatisfiedCount = 0.0f;
    {
      for (int iYBlock = 0; iYBlock < facilityNYBlocks; ++iYBlock) {
        yOffset = iYBlock*yBlockSize;
        nYValid = yBlockSize;
        if (iYBlock == facilityNYBlocks-1) nYValid = facilityYSize - yOffset;

        for (int iXBlock = 0; iXBlock < facilityNXBlocks; ++iXBlock) {
          xOffset = iXBlock*xBlockSize;
          nXValid = xBlockSize;
          if (iXBlock == facilityNXBlocks-1) nXValid = facilityXSize - xOffset;

          dataOffset = (targetNXBlocks * (blocksYOffset + iYBlock) + (blocksXOffset + iXBlock)) * (xBlockSize * yBlockSize);
          buffer = data + dataOffset;
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
    }

    // Each pixel under the isochrone will be multiplied by the proportion of
    // the unsatisfied demand that is satisfied by this facility.
    float capacity = capacities[iFacility];
    float factor = 1;
    if (unsatisfiedCount != 0) factor -= (capacity / unsatisfiedCount);
    if (factor < 0) factor = 0;

#ifdef DEBUG
    std::cerr << " Capacity is " << capacity << std::endl;
    std::cerr << " Unsatisfied count is " << unsatisfiedCount << std::endl;
    std::cerr << " Satisfaction factor is " << factor << std::endl;
#endif

    /****************************************************************************
     * Second pass to apply the multiplying factor to each pixel
     ****************************************************************************/
    {
      for (int iYBlock = 0; iYBlock < facilityNYBlocks; ++iYBlock) {
        yOffset = iYBlock*yBlockSize;
        nYValid = yBlockSize;
        if (iYBlock == facilityNYBlocks-1) nYValid = facilityYSize - yOffset;

        for (int iXBlock = 0; iXBlock < facilityNXBlocks; ++iXBlock) {
          xOffset = iXBlock*xBlockSize;
          nXValid = xBlockSize;
          if (iXBlock == facilityNXBlocks-1) nXValid = facilityXSize - xOffset;

          dataOffset = (targetNXBlocks * (blocksYOffset + iYBlock) + (blocksXOffset + iXBlock)) * (xBlockSize * yBlockSize);
          buffer = data + dataOffset;
          facilityBand->ReadBlock(iXBlock, iYBlock, facilityBuffer);

          for (int iY = 0; iY < nYValid; ++iY) {
            for (int iX = 0; iX < nXValid; ++iX) {
              int iBuff = xBlockSize*iY+iX;
              if (buffer[iBuff] != nodata && facilityBuffer[iBuff] != facilityNodata) {
                buffer[iBuff] *= factor;
              }
            }
          }
        }
      }
    }

    closeRaster(facilityDataset);
  }

  /****************************************************************************
   * Last pass on dataset to count total unsatisfied population
   ****************************************************************************/
  double totalUnsatisfied = 0.0;
  {
#ifdef BENCHMARK
    boost::timer::auto_cpu_timer t(std::cerr, 6, "Count total unsatisfied: %t sec CPU, %w sec real\n");
#endif

    for (int iYBlock = 0; iYBlock < targetNYBlocks; ++iYBlock) {
      yOffset = iYBlock*yBlockSize;
      nYValid = (iYBlock == targetNYBlocks-1) ? (targetYSize - yOffset) : yBlockSize;

      for (int iXBlock = 0; iXBlock < targetNXBlocks; ++iXBlock) {
        xOffset = iXBlock*xBlockSize;
        nXValid = (iXBlock == targetNXBlocks-1) ? (targetXSize - xOffset) : xBlockSize;

        dataOffset = (targetNXBlocks * iYBlock + iXBlock) * (xBlockSize * yBlockSize);

        for (int iY = 0; iY < nYValid; ++iY) {
          for (int iX = 0; iX < nXValid; ++iX) {
            int i = dataOffset + xBlockSize*iY+iX;
            if (data[i] != nodata) {
              totalUnsatisfied += data[i];
            }
          }
        }
      }
    }
  }

  /****************************************************************************
   * Write scaled-to-byte dataset to disk
   ****************************************************************************/
  {
#ifdef BENCHMARK
    boost::timer::auto_cpu_timer t(std::cerr, 6, "Write raster: %t sec CPU, %w sec real\n");
#endif
    char **papszOptions = NULL;
    papszOptions = CSLSetNameValue(papszOptions, "TILED", "YES");
    papszOptions = CSLSetNameValue(papszOptions, "BLOCKXSIZE", numberToString(xBlockSize).c_str());
    papszOptions = CSLSetNameValue(papszOptions, "BLOCKYSIZE", numberToString(yBlockSize).c_str());

    targetDataset = GetGDALDriverManager()->GetDriverByName("GTiff")->Create(targetFilename.c_str(), targetXSize, targetYSize, 1, GDT_Byte, papszOptions);
    targetDataset->SetGeoTransform(demoProjection);
    targetDataset->SetProjection(demoProjectionWKT.c_str());


    // Save calculated unsatisifiedDemand as metadata in the dataset file
    targetDataset->SetMetadataItem(DEMAND_METADATA_KEY, numberToString((long)totalUnsatisfied).c_str(), APP_METADATA_DOMAIN);

    GDALRasterBand* targetBand = targetDataset->GetRasterBand(1);
    targetBand->SetNoDataValue(0.0);

    BYTE* byteBuffer = (BYTE*) CPLMalloc(sizeof(BYTE)*xBlockSize*yBlockSize);

    for (int iYBlock = 0; iYBlock < targetNYBlocks; ++iYBlock) {
      yOffset = iYBlock*yBlockSize;
      nYValid = (iYBlock == targetNYBlocks-1) ? (targetYSize - yOffset) : yBlockSize;

      for (int iXBlock = 0; iXBlock < targetNXBlocks; ++iXBlock) {
        xOffset = iXBlock*xBlockSize;
        nXValid = (iXBlock == targetNXBlocks-1) ? (targetXSize - xOffset) : xBlockSize;

        dataOffset = (targetNXBlocks * iYBlock + iXBlock) * (xBlockSize * yBlockSize);

        for (int iY = 0; iY < nYValid; ++iY) {
          for (int iX = 0; iX < nXValid; ++iX) {
            int iBuff = xBlockSize*iY+iX;
            float value = data[dataOffset + iBuff];
            if (value != nodata) {
              if (value > saturation) {
                byteBuffer[iBuff] = 255;
              } else {
                byteBuffer[iBuff] = (BYTE)((value * 255) / saturation);
              }
            } else {
              byteBuffer[iBuff] = 0;
            }
          }
        }

        targetBand->WriteBlock(iXBlock, iYBlock, byteBuffer);
      }
    }

    targetDataset->FlushCache();
    closeRaster(targetDataset);
  }

  CPLFree(data);
  CPLFree(facilityBuffer);

  return totalUnsatisfied;
}

int main(int argc, char *argv[]) {
  std::vector<char*> args;

  if (argc == 2 && argv[1][0] == '@') {
    std::cerr << "Loading arguments from file " << &argv[1][1] << std::endl;;

    std::ifstream source;
    source.open(&argv[1][1], std::ifstream::in);
    if (!source.is_open()) {
      std::cerr << "Error opening file. Aborting." <<  std::endl;
      exit(1);
    }

    args.push_back(argv[0]);
    while(source.good()) {
      std::string arg;
      source >> arg;
      if (!arg.empty()) {
        char* cstr = new char[arg.length()+1];
        std::strcpy(cstr, arg.c_str());
        args.push_back(cstr);
      }
    }

    argc = args.size();
    argv = &args[0];
    source.close();
  }

  if (argc < 4 || (argc % 2) == 1) {
    std::cerr << "Usage: " << argv[0] << " TARGET.tif SATURATION POPULATION.tif FACILITYMASK1.tif CAPACITY1 ... FACILITYMASKN.tif CAPACITYN"
      << std::endl << std::endl
      << "Example:" << std::endl
      << " " << argv[0] << "out.tif 2000 data/populations/data/REGIONID.tif \\" << std::endl
      << " data/isochrones/REGIONID/POLYGONID1.tif 500 \\" << std::endl
      << " data/isochrones/REGIONID/POLYGONID2.tif 800" << std::endl << std::endl
      << "Resulting total unsatisfied demand is returned via STDOUT." << std::endl
      << "Note that if the TARGET file exists, it will not be recalculated, though the pre calculated unsatisfied demand will be returned." << std::endl;
    exit(1);
  }

  GDALAllRegister();
  long unsatisifiedDemand = 0;

  if (fileExists(argv[1])) {
#ifdef DEBUG
    std::cerr << "File " << argv[1] << " already exists." << std::endl;
#endif
    unsatisifiedDemand = readUnsatisfiedDemand(argv[1]);
  } else {
    std::vector<std::string> facilities;
    std::vector<float> capacities;
    for (int i = 4; i < argc; i++) {
      facilities.push_back(argv[i]);
      capacities.push_back(atof(argv[++i]));
    }

    unsatisifiedDemand = calculateUnsatisfiedDemand(argv[1], argv[3], atof(argv[2]), facilities, capacities);
  }

  std::cout << unsatisifiedDemand << std::endl;;
}
