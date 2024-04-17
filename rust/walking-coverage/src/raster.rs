use std::fmt::{Display, Formatter};

use gdal::{errors::Result, raster::GdalType, Dataset, GeoTransform};

#[derive(Debug, Clone)]
pub struct Coords {
    pub lng: f64,
    pub lat: f64,
}

impl Display for Coords {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "({}, {})", self.lng, self.lat)
    }
}

#[derive(Debug, Clone)]
pub struct PixelCoords {
    pub x: i32,
    pub y: i32,
}

impl Display for PixelCoords {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "({}, {})", self.x, self.y)
    }
}

pub struct BandData<T: GdalType> {
    pub size: (usize, usize),
    pub data: Vec<T>,
    pub nodata_value: T,
}

pub struct Raster {
    _dataset: Dataset,
    pub geo_transform: GeoTransform,
}

impl Raster {
    pub fn new(dataset: Dataset) -> Self {
        let geo_transform = dataset.geo_transform().unwrap();
        Self {
            _dataset: dataset,
            geo_transform,
        }
    }

    pub fn is_north_up(&self) -> bool {
        self.geo_transform[2] == 0.0
            && self.geo_transform[4] == 0.0
            && self.geo_transform[1] > 0.0
            && self.geo_transform[5] < 0.0
    }

    fn top_left(&self) -> Coords {
        Coords {
            lng: self.geo_transform[0],
            lat: self.geo_transform[3],
        }
    }
    fn bottom_right(&self) -> Coords {
        let raster_size = self._dataset.raster_size();
        Coords {
            lng: self.geo_transform[0] + self.geo_transform[1] * raster_size.0 as f64,
            lat: self.geo_transform[3] + self.geo_transform[5] * raster_size.1 as f64,
        }
    }

    fn pixel_width(&self) -> f64 {
        self.geo_transform[1]
    }
    fn pixel_height(&self) -> f64 {
        -self.geo_transform[5]
    }

    pub fn pixel_coords(&self, coords: &Coords) -> PixelCoords {
        let top_left = self.top_left();
        let pixel_width = self.pixel_width();
        let pixel_height = self.pixel_height();
        let x = ((coords.lng - top_left.lng) / pixel_width) as i32;
        let y = ((coords.lat - top_left.lat) / -pixel_height) as i32;
        PixelCoords { x, y }
    }

    pub fn contains(&self, coords: &Coords) -> bool {
        let top_left = self.top_left();
        let bottom_right = self.bottom_right();
        coords.lng >= top_left.lng
            && coords.lng <= bottom_right.lng
            && coords.lat >= bottom_right.lat
            && coords.lat <= top_left.lat
    }

    pub fn load_data_from_band(&mut self, band_index: isize) -> Result<BandData<f32>> {
        let raster_band = self._dataset.rasterband(band_index)?;
        let nodata_value = raster_band.no_data_value().unwrap_or(0.0);
        let buffer = raster_band.read_band_as()?;
        Ok(BandData {
            size: buffer.size,
            data: buffer.data,
            nodata_value: nodata_value as f32,
        })
    }
}
