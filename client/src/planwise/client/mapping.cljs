(ns planwise.client.mapping
  (:require [reagent.format :as fmt]
            [re-frame.utils :as c]
            [planwise.client.config :as config]))

(def emerald-mapbox-mapid "instedd/ckml2dykm4cl917o08mxux07b")
(def bright-mapbox-mapid "instedd/ckml2dykm4cl917o08mxux07b")
(def mapbox-access-token "pk.eyJ1IjoiaW5zdGVkZCIsImEiOiJja21ndHVrZ3cwMHQ5Mm9rZDgwaThkd3JoIn0.Kr3h9hO93IimCigXfYrBmw")

(def map-preview-position
  [-12.211180191503985 21.4453125])

(def map-preview-size
  {:height 158
   :width  256})

(def layer-name
  "population")

(def default-base-tile-layer
  [:tile-layer {:url "https://api.mapbox.com/styles/v1/{mapid}/tiles/512/{z}/{x}/{y}@2x?access_token={accessToken}"
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :zoomOffset -1
                :tileSize 512
                :mapid emerald-mapbox-mapid
                :accessToken mapbox-access-token}])

(def bright-base-tile-layer
  [:tile-layer {:url "https://api.mapbox.com/styles/v1/{mapid}/tiles/512/{z}/{x}/{y}@2x?access_token={accessToken}"
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :zoomOffset -1
                :tileSize 512
                :mapid bright-mapbox-mapid
                :accessToken mapbox-access-token}])

(def geojson-levels
  {1 {:ub 8, :simplify 0.1, :tileSize 10.0}
   2 {:lb 8, :ub 11, :simplify 0.01, :tileSize 2.0}
   3 {:lb 11, :simplify 0.0, :tileSize 1.0}})

(def geojson-first-level
  (-> geojson-levels keys first))

(defn geojson-level->simplify
  [level]
  (get-in geojson-levels [(js/parseInt level) :simplify]))

(defn simplify->geojson-level
  [simplify]
  (->> geojson-levels
       (filter (fn [[level {s :simplify}]] (= s simplify)))
       (map first)
       first))

;; only "classic" mapbox styles can be used with this static API.
;; the new API does not allow defining an overlay as a query parameter.
(defn static-image
  ([geojson]
   (static-image geojson map-preview-size))
  ([geojson options]
   (fmt/format "https://api.mapbox.com/styles/v1/%s/static/geojson(%s)/auto/%dx%d@2x?access_token=%s"
               emerald-mapbox-mapid
               (js/encodeURIComponent geojson)
               (:width options)
               (:height options)
               mapbox-access-token)))

(defn bbox-center [[[s w] [n e]]]
  [(/ (+ s n) 2.0) (/ (+ e w) 2.0)])

(def fullmap-region-geo
  "{
      \"type\": \"FeatureCollection\",
      \"features\": [
        {
          \"type\": \"Feature\",
          \"properties\": {
            \"stroke\": \"#555555\",
            \"stroke-width\": 2,
            \"stroke-opacity\": 0,
            \"fill\": \"#555555\",
            \"fill-opacity\": 0
          },
          \"geometry\": {
            \"type\": \"Polygon\",
            \"coordinates\": [
              [
                [
                  -72.0703125,
                  -50.51342652633955
                ],
                [
                  123.04687499999999,
                  -50.51342652633955
                ],
                [
                  123.04687499999999,
                  71.85622888185527
                ],
                [
                  -72.0703125,
                  71.85622888185527
                ],
                [
                  -72.0703125,
                  -50.51342652633955
                ]
              ]
            ]
          }
        }
      ]
    }")
