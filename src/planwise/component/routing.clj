(ns planwise.component.routing
  (:require [planwise.boundary.routing :as boundary]
            [integrant.core :as ig]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/routing.sql")

(defn algorithm-query-fn
  [algorithm]
  (case algorithm
    :alpha-shape isochrone-for-node-alpha-shape
    :buffer      isochrone-for-node-buffer
    (throw (IllegalArgumentException. (str "Invalid algorithm " algorithm)))))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord RoutingService [db]

  boundary/Routing
  (nearest-node [{:keys [db]} lat lon]
    (get-nearest-node (:spec db) {:lat lat :lon lon}))
  (compute-isochrone [{:keys [db]} node-id distance algorithm]
    (let [query-fn (algorithm-query-fn (or algorithm :alpha-shape))]
      (:poly (query-fn (:spec db) {:node-id node-id :distance distance})))))


;; ----------------------------------------------------------------------
;; Service initialization

(defmethod ig/init-key :planwise.component/routing
  [_ config]
  (map->RoutingService config))
