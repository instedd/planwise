(ns viewer.leaflet
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :as debug]))

(defn create-marker [[lat lon]]
  (let [latLng (.latLng js/L lat lon)]
    (.marker js/L latLng #js {:clickable false})))

(defmulti leaflet-layer first)

(defmethod leaflet-layer :default [layer-def]
  (throw (str "Unknown layer type " (first layer-def))))

(defmethod leaflet-layer :point-layer [[_ props & children]]
  (let [layer (.layerGroup js/L)
        points (:points props)]
    (doseq [point points] (.addLayer layer (create-marker point)))
    layer))

(defmethod leaflet-layer :geojson-layer [[_ props & children]]
  (let [layer (.geoJson js/L nil #js {:clickable false})
        data (:data props)]
    (when data (.addData layer data))
    layer))

;; TODO: move this into the leaflet-layer multi and define it via the children of the component

(defn add-mapbox-layer [leaflet]
  (.addTo (.tileLayer js/L "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}"
                      #js {:attribution "&copy; Mapbox"
                           :maxZoom 18
                           :mapid "ggiraldez.056e1919"
                           :accessToken "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA"})
          leaflet))

(defn leaflet-update-layers [this]
  (let [state (reagent/state this)
        children (reagent/children this)
        leaflet (:map state)
        old-children (:current-children state)
        layers (map leaflet-layer children)]
    (when-not (= old-children children)
      (println "updating layers")
      (when-let [old-layers (:layers state)]
        (doseq [old-layer old-layers] (.removeLayer leaflet old-layer)))
      (doseq [layer layers] (.addLayer leaflet layer))
      (reagent/set-state this {:layers layers
                               :current-children children}))))

(defn leaflet-update-viewport [this]
  (let [state (reagent/state this)
        props (reagent/props this)
        position (:position props)
        zoom (:zoom props)
        leaflet (:map state)]
    (reagent/set-state this {:position position :zoom zoom})
    (.setView leaflet (clj->js position) zoom)))

(defn leaflet-moveend-handler [this]
  (fn [e]
    (let [state (reagent/state this)
          props (reagent/props this)
          leaflet (:map state)
          center (.getCenter leaflet)
          new-pos [(.-lat center) (.-lng center)]
          new-zoom (.getZoom leaflet)
          current-pos (:position state)
          current-zoom (:zoom state)
          on-position-changed (:on-position-changed props)
          on-zoom-changed (:on-zoom-changed props)]
      (when (and on-position-changed (not= new-pos current-pos))
        (on-position-changed new-pos))
      (when (and on-zoom-changed (not= new-zoom current-zoom))
        (on-zoom-changed new-zoom)))))

(defn leaflet-click-handler [this]
  (fn [e]
    (when-let [on-click (:on-click (reagent/props this))]
      (let [latlng (.-latlng e)
            lat (.-lat latlng)
            lon (.-lng latlng)]
        (on-click lat lon)))))

(defn leaflet-did-mount [this]
  (let [leaflet (.map js/L (reagent/dom-node this))
        props (reagent/props this)
        points (:points props)
        geojson (:geojson props)
        children (reagent/children this)]
    (reagent/set-state this {:map leaflet})

    (add-mapbox-layer leaflet)

    (leaflet-update-layers this)
    (leaflet-update-viewport this)

    (.on leaflet "moveend" (leaflet-moveend-handler this))
    (.on leaflet "click" (leaflet-click-handler this))))

(defn leaflet-did-update [this]
  (let [state (reagent/state this)
        leaflet (:map state)]

    (leaflet-update-layers this)
    (leaflet-update-viewport this)))

(defn leaflet-render [props & children]
  [:div {:style {:height "600px"}}])

(defn map-widget [props]
  (reagent/create-class {:display-name "leaflet/map-widget"
                         :reagent-render leaflet-render
                         :component-did-mount leaflet-did-mount
                         :component-did-update leaflet-did-update}))
