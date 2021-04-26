(ns planwise.client.projects2.db
  (:require [schema.core :as s]
            [planwise.client.asdf :as asdf]))

(def initial-db
  {:current-project         nil
   :list                    nil
   :source-types            #{"raster" "points"}})
