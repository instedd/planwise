(ns planwise.client.mapping
  (:require [reagent.format :as fmt]
            [re-frame.utils :as c]
            [planwise.client.config :as config]))

(def mapbox-default-mapid "instedd/ckww8bpbs6d4f15qj408iip8c")
(def mapbox-base-mapid "instedd/ckww8oi571asn14plvqixm5g4")
(def mapbox-labels-mapid "instedd/ckww8sgct1auc15pcoeo01pv1")
(def mapbox-access-token "pk.eyJ1IjoiaW5zdGVkZCIsImEiOiJja21ndHVrZ3cwMHQ5Mm9rZDgwaThkd3JoIn0.Kr3h9hO93IimCigXfYrBmw")

(def map-preview-position
  [-12.211180191503985 21.4453125])

(def map-preview-size
  {:height 158
   :width  256})

(def layer-name
  "population")

(defn- mapbox-layer
  [{:keys [key mapid]}]
  [:tile-layer {:key         key
                :url         "https://api.mapbox.com/styles/v1/{mapid}/tiles/512/{z}/{x}/{y}@2x?access_token={accessToken}"
                :attribution (str "&copy; <a target='_blank' href='https://www.mapbox.com/about/maps'>Mapbox</a> "
                                  "&copy; <a target='_blank' href='http://www.openstreetmap.org/about/'>OpenStreetMap</a>")
                :maxZoom     18
                :zoomOffset  -1
                :tileSize    512
                :mapid       mapid
                :accessToken mapbox-access-token}])

(def default-base-tile-layer
  (mapbox-layer {:key "default-layer" :mapid mapbox-default-mapid}))

(def base-tile-layer
  (mapbox-layer {:key "base-layer" :mapid mapbox-base-mapid}))

(def labels-tile-layer
  (mapbox-layer {:key "labels-layer" :mapid mapbox-labels-mapid}))

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
   (fmt/format "https://api.mapbox.com/styles/v1/%s/static/geojson(%s)/auto/%dx%d@2x?access_token=%s&logo=false"
               mapbox-default-mapid
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
