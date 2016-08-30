# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

## [0.5.1] - 2016-08-30
### Fixed
- Fix two importer related bugs: don't stop importing after the first page; and
  don't update import stats twice when processing the last facility (#188)
- Fix number formatting in Safari (#193)

## [0.5.0] - 2016-08-30
### Added
- Projects are now owned by users and each user has access to their own projects
  only. A project sharing feature will be added in the next release (#129)
- Projects can now be deleted.
- The system now supports multiple sets of facilities (datasets) which are owned
  by the user who creates them. When creating a project the user is asked to
  choose an existing dataset (#154)
- Facilities on the map are rendered in a different colour depending on their
  type and have an associated popup showing the facility's name and type (#147)
- Region population is shown in project listing and in the project view, along
  with area in km2 and population density (#106)
- Show loading indicator if requesting isochrones takes more than 2 seconds (#140)
- Show a population density legend in the map (#109)

### Changes and improvements
- Isochrones for facilities are now requested incrementally from the server and
  cached client-side, reducing the bandwidth requirements for general use (#78, #139)
- The import process can now calculate isochrones for facilities in parallel,
  reducing the overall importing time (#92)
- Improved the result reporting for a dataset import process. If any errors or
  warnings occurred during the process, this is shown in a summarized view to
  the user (#114)
- Only show valid (with the required fields) Resourcemap collections when
  selecting one for importing to a dataset.
- UI has been reviewed and tweaked in several places for improved usability.
  Changed default font and added icons (#111, #146)
- Map colours and general appearence has been changed to improve readability (#149)
- Facilities more than 1km further away from the nearest road will be ignored
  for isochrone calculation and shown in the map with a different colour (#89)
- Changed wording in facility types filter texts for clarification (#148)
- Added clarification in transport time section indicating that travel time is
  one-way (#148)
- Added Plumatic's Schema to validate data structures. Added several schemas and
  enabled validation in a couple of places.
- Several refactors and code cleanups to keep the code base extensible and
  maintainable.

### Fixed
- Session will not expire after 1 hour and the application will continue to work
  without reloading the page (#72)
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
