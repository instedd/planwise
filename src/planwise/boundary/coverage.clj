(ns planwise.boundary.coverage)

(defprotocol CoverageService
  "Computation of coverages from geographical points, given some criteria"

  (supported-algorithms [this]
    "Enumerate the supported algorithms")

  (compute-coverage [this point criteria]
    "Computes a coverage area from a given geographical point"))
