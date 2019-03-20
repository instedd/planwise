(ns planwise.boundary.coverage
  (:require [clojure.spec.alpha :as s]
            [clojure.math.combinatorics :as combo]))

;; Specs =====================================================================
;;

(s/def ::algorithm keyword?)
(s/def ::base-criteria (s/keys :req-un [::algorithm]))

(defmulti criteria-algo :algorithm)
(s/def ::coverage-criteria (s/multi-spec criteria-algo :algorithm))

(s/def ::pixel-resolution number?)
(s/def ::xres ::pixel-resolution)
(s/def ::yres ::pixel-resolution)
(s/def ::raster-resolution (s/keys :req-un [::xres ::yres]))

(s/def ::context-options (s/keys :req-un [::region-id ::coverage-criteria]
                                 :opt-un [::raster-resolution]))

;; Protocol defintions =======================================================
;;

(defprotocol CoverageService
  "Computation of coverages from geographical points, given some criteria"

  (supported-algorithms [this]
    "Enumerate the supported algorithms")

  (compute-coverage-polygon [this point criteria]
    "Computes a coverage area from a given geographical point"))


(defprotocol CoverageUtilities
  "Extra function utilities for running algorithms"

  (locations-outside-polygon [this polygon locations]
    "Given a polygon and set of locations returns those outside the geometry")

  (geometry-intersected-with-project-region [this geometry region-id]
    "Given a coverage geometry intersects it with project region
    Returns result as geojson")

  (get-max-distance-from-geometry [this geometry]
    "Retrieves max distance in geometry"))


(defprotocol CoverageContexts
  "Functions for managing coverage contexts"

  (setup-context [this context-id options]
    "Creates a new coverage context with the given options

    If a context already exists for the id, checks options equality and
    invalidates all computed coverages if they differ.

    Options include:
    - raster resolution (if applicable, only needed for raster scenarios currently)
    - coverage algorithm and its options
    - region id to intersect the coverage")

  (delete-context [this context-id]
    "Deletes a coverage context with all related computed coverages")

  (resolve-coverages! [this context-id locations]
    "Using the given context id, resolve coverage for all locations

    Returns the location ids with a status of the resolution.

    Locations should consist of an id and geographical coordinates latitude and
    longitude. Checks previously computed coverages if they exist and computes
    new ones.")

  (query-coverages [this context-id ids query]
    "Performs a query over the given coverage ids; returned values depend on the
    value of the query parameter

    Query indicates the required return information:
    - nil
        only returns the status for the coverage
    - :raster
        returns the path to the raster coverage mask
    - :geojson
        returns the GeoJSON for the selected locations
    - [:sources-covered <source-set-id>]
        returns an enumeration of the sources from the source set which are
        covered by each coverage"))


;; Auxiliary functions =======================================================
;;

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


;; REPL testing ==============================================================
;;

(comment
  (def service (:planwise.component/coverage integrant.repl.state/system))

  (enumerate-algorithm-options service :walking-friction))
