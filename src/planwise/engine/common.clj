(ns planwise.engine.common
  (:require [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.sources :as sources-set]
            [planwise.boundary.coverage :as coverage]
            [planwise.engine.raster :as raster]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.engine.demand :as demand]
            [planwise.util.files :as files]
            [clojure.set :as set]
            [planwise.util.collections :refer [sum-by]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn provider-coverage-raster-path
  "Full path to the raster coverage for a provider set, given the raster property of a provider."
  [provider-set-id raster]
  (str "data/coverage/" provider-set-id "/" raster ".tif"))

(defn filter-options-for-project
  "Filter options from project for usage in providers-set/get-providers-in-region."
  [project]
  (let [region-id (:region-id project)
        tags      (get-in project [:config :providers :tags])]
    {:region-id region-id
     :tags      tags}))

(defn- provider-mapper
  "Returns a mapper function for providers into the shape required for computing scenarios."
  [provider-set-id applicable]
  (fn [provider]
    (-> provider
        (select-keys [:id :lat :lon :name :capacity])
        (assoc :applicable? applicable))))

(defn providers-in-project
  "Fetches all providers in the project's region and builds a collection with an
  extra attribute indicating if they apply (wrt. tag filtering). Returned
  providers have :id, :name, :capacity, :applicable."
  [providers-service project]
  (let [provider-set-id (:provider-set-id project)
        provider-set    (providers-set/get-provider-set providers-service provider-set-id)
        version         (or (:provider-set-version project)
                            (:last-version provider-set))
        filter-options  (filter-options-for-project project)
        all-providers   (providers-set/get-providers-in-region providers-service
                                                               provider-set-id
                                                               version
                                                               filter-options)
        applicable      (map (provider-mapper provider-set-id true) (:providers all-providers))
        non-applicable  (map (provider-mapper provider-set-id false) (:disabled-providers all-providers))
        providers       (concat applicable non-applicable)]
    (->> providers
         (sort-by :capacity)
         reverse)))
