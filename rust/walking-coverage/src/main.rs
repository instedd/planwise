use std::path::PathBuf;

use clap::Parser;
use gdal::Dataset;
use thiserror::Error;

mod raster;
use raster::{Coords, Raster};

#[derive(Error, Debug)]
#[error("invalid coordinates")]
struct ParseCoordsError;

fn parse_coords(s: &str) -> Result<Coords, Box<dyn std::error::Error + Send + Sync + 'static>> {
    let (lng, lat) = s.split_once(',').ok_or(ParseCoordsError)?;

    let lng_fromstr = lng.parse::<f64>().map_err(|_| ParseCoordsError)?;
    let lat_fromstr = lat.parse::<f64>().map_err(|_| ParseCoordsError)?;

    Ok(Coords {
        lng: lng_fromstr,
        lat: lat_fromstr,
    })
}

#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
struct Options {
    /// Input friction raster file
    #[arg(short = 'i', long = "input-friction-raster", value_name = "FILE")]
    raster_path: PathBuf,

    /// Output cost raster path
    #[arg(short = 'o', long = "output-cost-raster", value_name = "FILE")]
    output_cost_path: Option<PathBuf>,

    /// Coordinates of origin given in lng,lat format
    #[arg(short = 'g', long, value_parser = parse_coords)]
    origin: Coords,

    /// Maximum time given in minutes
    #[arg(short = 'm', long = "max-time", default_value = "180")]
    max_time_cost: Vec<i32>,

    /// Minimum friction to consider in min/m
    #[arg(short = 'f', long = "min-friction", default_value = "0.01")]
    min_friction: Vec<f32>,

    #[arg(short = 'v', long, default_value_t = false)]
    verbose: bool,
}

fn main() {
    let options = Options::parse();

    let mut friction_raster = Raster::new(Dataset::open(&options.raster_path).unwrap());
    assert!(
        friction_raster.is_north_up(),
        "Raster must be normalized with 'north-up'"
    );

    if options.verbose {
        eprintln!(
            "Using friction raster file: {}",
            options.raster_path.to_str().unwrap()
        );
        eprintln!("Using origin at {}", options.origin);
    }

    assert!(friction_raster.contains(&options.origin), "Origin out of boundaries of raster file");
    let _pixel_origin = friction_raster.pixel_coords(&options.origin);

    let friction_data = friction_raster.load_data_from_band(1).unwrap();
    dbg!(friction_data.size);
}
