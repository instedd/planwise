# Importing friction data

Download the global friction raster from The Malaria Atlas Project: 
https://malariaatlas.org/research-project/accessibility_to_cities/

Direct download link:
https://malariaatlas.org/geoserver/ows?service=CSW&version=2.0.1&request=DirectDownload&ResourceId=Explorer:2015_friction_surface_v1_Decompressed

The zip file contains a .tif file with the friction layer for most of the
planet. Unit is minutes/meter. Unzip it and execute the following script to clip
by the country-level regions used by Planwise:

```sh
scripts/friction/load-friction-raster PATH_TO_GLOBAL_FRICTION.tif
```

The script will retrieve the admin-level 0 regions from the database and clip
the raster by the contour of each region, saving them in
`$DATA_PATH/friction/regions`.

The clips will be used when computing coverage for a site. The clip will be
selected by inclusion of the site in the region polygon.
