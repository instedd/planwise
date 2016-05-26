(ns viewer.leaflet
  (:require [reagent.core :as reagent :refer [atom]]))

(declare leaflet-draw-points)
(declare leaflet-draw-geojson)

(defn add-mapbox-layer [leaflet]
  (.addTo (.tileLayer js/L "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}"
                      #js {:attribution "&copy; Mapbox"
                           :maxZoom 18
                           :mapid "ggiraldez.056e1919"
                           :accessToken "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA"})
          leaflet))

;; TODO: the event handlers are not changed when updating props because they are
;; bound here at mount time.

(defn leaflet-did-mount [this]
  (let [leaflet (.map js/L (reagent/dom-node this))
        props (reagent/props this)
        position (:position props)
        zoom (:zoom props)
        on-position-changed (:on-position-changed props)
        on-zoom-changed (:on-zoom-changed props)
        on-click (:on-click props)
        points (:points props)
        geojson (:geojson props)]
    (reagent/set-state this {:map leaflet
                             :position position
                             :zoom zoom})
    (leaflet-draw-points this points)
    (leaflet-draw-geojson this geojson)
    (.setView leaflet (clj->js position) zoom)
    (add-mapbox-layer leaflet)
    (.on leaflet "moveend" (fn [e]
                             (let [c (.getCenter leaflet)
                                   new-pos [(.-lat c) (.-lng c)]
                                   new-zoom (.getZoom leaflet)
                                   state (reagent/state this)
                                   current-pos (:position state)
                                   current-zoom (:zoom state)]
                               (when (and on-position-changed (not= new-pos current-pos))
                                 (on-position-changed new-pos))
                               (when (and on-zoom-changed (not= new-zoom current-zoom))
                                 (on-zoom-changed new-zoom)))))
    (.on leaflet "click" (fn [e]
                           (when on-click
                             (let [latlng (.-latlng e)
                                   lat (.-lat latlng)
                                   lon (.-lng latlng)]
                               (on-click lat lon)))))))


(defn create-circle [[lat lon]]
  (let [latLng (.latLng js/L lat lon)]
    (.marker js/L latLng #js {:clickable false})))

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

(defn leaflet-draw-geojson [this geojson]
  (let [state (reagent/state this)
        leaflet (:map state)
        old-geojson (:geojson state)
        old-geolayer (:geolayer state)]
    (when (not= geojson old-geojson)
      (let [new-layer (.geoJson js/L geojson #js {:clickable false})]
        (println "updating geojson")

        (when (some? old-geolayer)
          (.removeLayer leaflet old-geolayer))
        (.addTo new-layer leaflet)
        (reagent/set-state this {:geojson geojson
                                 :geolayer new-layer})))))

(defn leaflet-did-update [this]
  (let [props (reagent/props this)
        leaflet (:map (reagent/state this))
        position (:position props)
        zoom (:zoom props)
        points (:points props)
        geojson (:geojson props)]
    (.setView leaflet (clj->js position) zoom)
    (reagent/set-state this {:position position :zoom zoom})
    (leaflet-draw-points this points)
    (leaflet-draw-geojson this geojson)))

(defn map-render []
  [:div {:style {:height "600px"}}])

(defn map-widget [props]
  (reagent/create-class {:reagent-render map-render
                         :component-did-mount leaflet-did-mount
                         :component-did-update leaflet-did-update}))
