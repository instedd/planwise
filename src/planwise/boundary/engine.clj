(ns planwise.boundary.engine)

(defprotocol Engine
  (compute-initial-scenario [this project]
    "Computes the initial scenario for a project")

  (compute-scenario [this project initial-scenario scenario]
    "Computes the scenario for a project. It assumes the initial scenario was created.")

  (clear-project-cache [this project-id]
    "Clears a project cache")

  (search-optimal-locations
    [engine project source]
    "According to project configuration and initial demand set
     returns suggestions for creating new provider")

  (search-optimal-interventions
    [engine project scenario settings]
    "Returns suggestions for either upgrading or increasing existing providers in project")

  (compute-scenario-stats
    [engine project scenario params]
    "Computes statistics for a scenario"))
