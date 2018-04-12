(ns planwise.boundary.engine)

(defprotocol Engine
  (compute-initial-scenario [this project]
    "Computes the initial scenario for a project"))
