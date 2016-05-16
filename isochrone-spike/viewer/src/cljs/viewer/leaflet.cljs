(ns viewer.leaflet
  (:require [reagent.core :as reagent :refer [atom]]))

(declare leaflet-draw-points)

(defn leaflet-did-mount [this]
  (let [map (.map js/L "map")
        props (reagent/props this)
        position (:position props)
        zoom (:zoom props)
        on-position-changed (:on-position-changed props)
        on-zoom-changed (:on-zoom-changed props)
        points (:points props)]
    (reagent/set-state this {:map map
                             :position position
                             :zoom zoom})
    (leaflet-draw-points this points)
    (.setView map (clj->js position) zoom)
    (.addTo (.tileLayer js/L "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}"
                        (clj->js {:attribution "&copy; Mapbox"
                                  :maxZoom 18
                                  :mapid "ggiraldez.056e1919"
                                  :accessToken "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA"}))
            map)
    (.on map "moveend" (fn [e]
                         (let [c (.getCenter map)
                               new-pos [(.-lat c) (.-lng c)]
                               new-zoom (.getZoom map)
                               state (reagent/state this)
                               current-pos (:position state)
                               current-zoom (:zoom state)]
                           (when (and on-position-changed (not= new-pos current-pos))
                             (on-position-changed new-pos))
                           (when (and on-zoom-changed (not= new-zoom current-zoom))
                             (on-zoom-changed new-zoom)))))))


(defn create-circle [[lat lon]]
  (let [latLng (.latLng js/L lat lon)]
    (.circleMarker js/L latLng)))

(defn leaflet-draw-points [this points]
  (let [state (reagent/state this)
        leaflet (:map state)
        old-points (:points state)
        old-shapes (:shapes state)]
    (when (not= points old-points)
      (let [new-shapes (mapv create-circle points)]
        (println "updating shapes")
        (doseq [old-shape old-shapes] (.removeLayer leaflet old-shape))
        (doseq [new-shape new-shapes] (.addTo new-shape leaflet))
        (reagent/set-state this {:points points
                                 :shapes new-shapes})))))

(defn leaflet-did-update [this]
  (let [props (reagent/props this)
        map (:map (reagent/state this))
        position (:position props)
        zoom (:zoom props)
        points (:points props)]
    (.setView map (clj->js position) zoom)
    (reagent/set-state this {:position position :zoom zoom})
    (leaflet-draw-points this points)))

(defn map-render []
  [:div#map {:style {:height "360px"}}])

(defn map-component [props]
  (reagent/create-class {:reagent-render map-render
                         :component-did-mount leaflet-did-mount
                         :component-did-update leaflet-did-update}))
