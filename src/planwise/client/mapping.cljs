(ns planwise.client.mapping
  (:require [reagent.format :as fmt]
            [re-frame.utils :as c]))

(def emerald-mapbox-mapid "juanedi.177h17ed")
(def bright-mapbox-mapid "juanedi/cis9iabkx002c31lg5awqyqa8")
(def mapbox-access-token "pk.eyJ1IjoianVhbmVkaSIsImEiOiJFeVIxckN3In0.502Q6lu_hD-Bu3r9a0jUyw")

(def map-preview-position
  [-12.211180191503985 21.4453125])

(def map-preview-size
  {:height 158
   :width  256})

(def layer-name
  "population")

(def default-base-tile-layer
  [:tile-layer {:url "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}"
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :mapid emerald-mapbox-mapid
                :accessToken mapbox-access-token}])

(def bright-base-tile-layer
  [:tile-layer {:url "https://api.mapbox.com/styles/v1/{mapid}/tiles/256/{z}/{x}/{y}?access_token={accessToken}"
                :attribution "&copy; Mapbox"
                :maxZoom 18
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
(defn static-image [geojson]
  (fmt/format "https://api.mapbox.com/v4/%s/geojson(%s)/auto/%dx%d.png?access_token=%s"
    emerald-mapbox-mapid
    (js/encodeURIComponent geojson)
    (:width map-preview-size)
    (:height map-preview-size)
    mapbox-access-token))

(defn bbox-center [[[s w] [n e]]]
  [(/ (+ s n) 2.0) (/ (+ e w) 2.0)])

(defn demand-map
  "Returns the full DATAFILE value to provide to mapserver to render a demand map,
   given a demand map key obtained from planwise.component.maps/demand-map-key"
  [map-key]
  (some->> map-key
    (str "demands/")))

(defn region-map
  "Returns the full DATAFILE value to provide to mapserver to render a population region map,
   given the id of the region"
  [region-id]
  (some->> region-id
    (str "populations/")))
