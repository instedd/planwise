(ns planwise.component.engine
  (:require [planwise.boundary.engine :as boundary]
            [planwise.boundary.projects2 :as projects2]
            [integrant.core :as ig]))

(defrecord Engine [projects2]
  boundary/Engine)

(defmethod ig/init-key :planwise.component/engine
  [_ config]
  (map->Engine config))
