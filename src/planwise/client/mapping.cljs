(ns planwise.client.mapping
  (:require [reagent.format :as fmt]))

(def mapbox-tile-url "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}")
(def mapbox-mapid "ggiraldez.056e1919")
(def emerald-mapbox-mapid "ggiraldez.0o0gmkco")
(def gray-mapbox-mapid "ggiraldez.0nk582m0")
(def mapbox-access-token "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA")

(def default-base-tile-layer
  [:tile-layer {:url mapbox-tile-url
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :mapid emerald-mapbox-mapid
                :accessToken mapbox-access-token}])

(def gray-base-tile-layer
  [:tile-layer {:url mapbox-tile-url
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :mapid gray-mapbox-mapid
                :accessToken mapbox-access-token}])

(defn static-image [geojson]
  (fmt/format "https://api.mapbox.com/v4/%s/geojson(%s)/auto/256x144.png?access_token=%s"
    emerald-mapbox-mapid
    (js/encodeURIComponent geojson)
    mapbox-access-token))

(defn bbox-center [[[s w] [n e]]]
  [(/ (+ s n) 2.0) (/ (+ e w) 2.0)])
