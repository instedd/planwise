(ns planwise.boundary.coverage
  (:require [clojure.math.combinatorics :as combo]))

(defprotocol CoverageService
  "Computation of coverages from geographical points, given some criteria"

  (supported-algorithms [this]
    "Enumerate the supported algorithms")

  (compute-coverage [this point criteria]
    "Computes a coverage area from a given geographical point")

  (locations-outside-polygon [this polygon locations]
    "Given a polygon and set of locations returns those outside the geometry")

  (get-max-distance-from-geometry [this geometry]
    "Retrieves max distance in geometry")

  (geometry-intersected-with-project-region [this geometry region-id]
    "Given a coverage geometry intersects it with project region
    Returns result as geojson"))

(defn enumerate-algorithm-options
  [service algorithm]
  (let [supported   (supported-algorithms service)
        description (get supported algorithm)
        criteria    (:criteria description)
        options     (for [[key key-desc] criteria :when (#{:enum} (:type key-desc))]
                      (map (juxt (constantly key) :value) (:options key-desc)))]
    (some->> options
             seq
             (apply combo/cartesian-product)
             (map (partial into {})))))


;; REPL testing

(comment
  (def service (:planwise.component/coverage integrant.repl.state/system))

  (enumerate-algorithm-options service :pgrouting-alpha))
