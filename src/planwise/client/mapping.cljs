(ns planwise.client.mapping
  (:require [reagent.format :as fmt]))

(def mapbox-tile-url "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}")
(def mapbox-mapid "ggiraldez.056e1919")
(def mapbox-access-token "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA")

(def default-base-tile-layer
  [:tile-layer {:url mapbox-tile-url
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :mapid mapbox-mapid
                :accessToken mapbox-access-token}])

(def kenya-geojson
  "{\"type\": \"FeatureCollection\", \"features\": [{\"type\":\"Feature\", \"properties\": {}, \"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[40.993,-0.85829],[41.58513,-1.68325],[40.88477,-2.08255],[40.63785,-2.49979],[40.26304,-2.57309],[40.12119,-3.27768],[39.80006,-3.68116],[39.60489,-4.34653],[39.20222,-4.67677],[37.7669,-3.67712],[37.69869,-3.09699],[34.07262,-1.05982],[33.90371119710453,-0.95],[33.893568969666944,0.109813537861896],[34.18,0.515],[34.6721,1.17694],[35.03599,1.90584],[34.59607,3.05374],[34.47913,3.5556],[34.005,4.249884947362048],[34.62019626785388,4.847122742081988],[35.29800711823298,5.506],[35.817447662353516,5.338232082790797],[35.817447662353516,4.77696566346189],[36.159078632855646,4.447864127672769],[36.85509323800812,4.447864127672769],[38.120915,3.598605],[38.43697,3.58851],[38.67114,3.61607],[38.89251,3.50074],[39.55938425876585,3.42206],[39.85494,3.83879],[40.76848,4.25702],[41.1718,3.91909],[41.85508309264397,3.918911920483727],[40.98105,2.78452],[40.993,-0.85829]]]}}]}")

(defn static-image [geojson]
  (fmt/format "https://api.mapbox.com/v4/%s/geojson(%s)/auto/256x144.png?access_token=%s"
    mapbox-mapid
    (js/encodeURIComponent geojson)
    mapbox-access-token))

(defn bbox-center [[[s w] [n e]]]
  [(/ (+ s n) 2.0) (/ (+ e w) 2.0)])
