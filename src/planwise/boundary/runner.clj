(ns planwise.boundary.runner)

(defprotocol Runner
  (run-external [service kind timeout name args]))
