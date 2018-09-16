(ns planwise.boundary.engine)

(defprotocol Engine
  (compute-initial-scenario [this project]
    "Computes the initial scenario for a project")

  (compute-scenario [this project initial-scenario scenario]
    "Computes the scenario for a project. It assumes the initial scenario was created.")

  (clear-project-cache [this project-id]
    "Clears a project cache")

  (search-optimal-location
    [engine project source]
    "Given initial set returns suggestions for new provider"))

