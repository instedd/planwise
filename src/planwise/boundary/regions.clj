(ns planwise.boundary.regions)

(defprotocol Regions
  "API for managing regions."

  (list-regions [this]
    "Returns all regions in the database.")

  (list-regions-with-preview [this ids]
    "Returns regions including a preview geojson of their boundaries given their
    ids.")

  (list-regions-with-geo [this ids simplify]
    "Returns regions including a simplified geojson with their boundaries given
    their ids.")

  (find-region [this id]
    "Returns region by ID, or nil if not found, without including any
    geometries")

  (enum-regions-inside-envelope [this envelope]
    "Return region ids which are fully contained in the given envelope")

  (get-region-geometry [this id]
    "Return the region geometry and bounding box as GeoJSON"))
