#!/usr/bin/env bash

# - Choose a different type for the band
gdal_rasterize -ts 9601 12179 -te 33.9126084 -4.6780661 41.9131217 5.4706946 -burn 255 polygon.geojson polygon.tif
