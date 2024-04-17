use std::ffi::c_int;

use gdal::{errors, raster::GdalDataType, Dataset};
use gdal_sys::{self, CPLErr, GDALRasterBandH};

fn aggregate_band(dataset: Dataset, band_no: isize) -> errors::Result<(f64, f32)> {
    let band = dataset.rasterband(band_no).unwrap();
    assert_eq!(
        band.band_type(),
        GdalDataType::Float32,
        "Raster band is not Float32"
    );
    let no_data = band.no_data_value().unwrap_or(0.0) as f32;
    let mut max_value: f32 = 0.0;
    let mut sum_value: f64 = 0.0;

    let (block_x_size, block_y_size) = band.block_size();
    let (raster_x_size, raster_y_size) = dataset.raster_size();
    let x_blocks = (raster_x_size + block_x_size - 1) / block_x_size;
    let y_blocks = (raster_y_size + block_y_size - 1) / block_y_size;

    // Buffer for reading raster blocks
    let pixels = block_x_size * block_y_size;
    let mut data: Vec<f32> = Vec::with_capacity(pixels);

    for iy_block in 0..y_blocks {
        let offset_y = iy_block * block_y_size;
        let span_y = if iy_block == y_blocks - 1 {
            raster_y_size - offset_y
        } else {
            block_y_size
        };
        for ix_block in 0..x_blocks {
            let offset_x = ix_block * block_x_size;
            let span_x = if ix_block == x_blocks - 1 {
                raster_x_size - offset_x
            } else {
                block_x_size
            };
            // Using the higher-level API from GDAL allocates a new buffer for each block read
            //let block = band.read_block::<f32>((ix_block, iy_block))?;
            //let raw_data = block.as_slice().unwrap();

            // Use the low-level API from GDAL to read blocks directly into the buffer vector
            // This avoids allocating a new buffer each time
            let rv = unsafe {
                gdal_sys::GDALReadBlock(
                    band.c_rasterband(),
                    ix_block as c_int,
                    iy_block as c_int,
                    data.as_mut_ptr() as GDALRasterBandH,
                )
            };
            if rv != CPLErr::CE_None {
                panic!("Error reading raster block");
            }
            unsafe {
                data.set_len(pixels);
            };
            let raw_data = data.as_slice();
            // End low-level, unsafe section of code

            for iy in 0..span_y {
                for ix in 0..span_x {
                    let value = raw_data[ix + iy * block_x_size];
                    if value != no_data {
                        sum_value += value as f64;
                        max_value = max_value.max(value);
                    }
                }
            }
        }
    }

    Ok((sum_value, max_value))
}

fn main() {
    let raster_path = std::env::args().nth(1).expect("No raster file path given");

    let ds = Dataset::open(raster_path).unwrap();
    let (sum, max) = aggregate_band(ds, 1).unwrap();
    println!("{} {}", sum as i64, max.ceil() as i64);
}
