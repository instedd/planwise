(ns planwise.boundary.engine)

(defprotocol Engine
  (compute-initial-scenario [this project]
    "Computes the initial scenario for a project")

  (clear-project-cache [this project-id]
    "Clears a project cache"))

