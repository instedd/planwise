# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added
- Facilities on the map have an associated popup showing the facility's name and
  type.
- Projects are now owned by users and each user has access to their own projects
  only.

### Fixed
- Improved new project validation (#96)
- Stop facilities from disappearing when changing transport time (#103)

## [0.4.0] - 2016-08-02
### The "Good Enough" Release

- Calculate catchment areas for facilities using isochrones over a road network
  from OpenStreetMap.
- Import facilities from InSTEDD's Resourcemap (http://resourcemap.instedd.org).
- Allow filtering facilities by type before showing them on the map.
- Overlay demographics layer with population density over the map.
- Show catchment area for filtered facilities using isochrones at fixed time settings.
