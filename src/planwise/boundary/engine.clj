(ns planwise.boundary.engine)

(defprotocol Engine
  (compute-initial-scenario [this project]
    "Computes the initial scenario for a project")

  (compute-scenario [this project scenario]
    "Computes the scenario for a project. It assumes the initial scenario was created.")

  (clear-project-cache [this project-id]
    "Clears a project cache")

  (search-optimal-location
    [engine project scenario]
    [engine project scenario raster]
    "Given a scenario returns suggestions for new provider"))

