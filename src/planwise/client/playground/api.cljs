(ns planwise.client.playground.api
  (:require [ajax.core :refer [GET POST]]
            [planwise.client.api :refer [json-request raw-request]]))

(defn fetch-geojson [& fns]
  (GET "/data/poly.geojson" (raw-request {} fns :mapper-fn #(.parse js/JSON %))))

;; The threshold parameter for isochrones should be given in seconds
;; Here the traffic-constant is an approximate modifier to the threshold using
;; Google Maps directions for comparison. The topology graph was built using the
;; mapconfig.xml in this repo.

(def traffic-constant 0.66)

(defn fetch-isochrone [node-id threshold algorithm & handler-fns]
    (GET "/api/routing/isochrone" (raw-request {:node-id node-id
                                                :threshold (* threshold traffic-constant)
                                                :algorithm (some-> algorithm name)}
                                          handler-fns
                                          :mapper-fn #(.parse js/JSON %))))

(defn parse-node-info [node-data]
  (when-not (empty? node-data)
    (let [node-id (node-data "id")
          geojson (.parse js/JSON (node-data "point"))
          point (reverse (js->clj (aget geojson "coordinates")))]
      {:node-id node-id
       :point point})))

(defn fetch-nearest-node [lat lon & fns]
  (let [params {:lat lat :lon lon}]
    (GET "/api/routing/nearest-node" (raw-request params fns :mapper-fn parse-node-info))))

(defn fetch-facilities [& fns]
  (GET "/api/facilities" (raw-request {} fns)))

(defn fetch-facilities-with-isochrones [threshold algorithm simplify & fns]
  (let [params {:threshold threshold
                :algorithm algorithm
                :simplify simplify}]
    (GET "/api/facilities/with-isochrones" (raw-request params fns))))
