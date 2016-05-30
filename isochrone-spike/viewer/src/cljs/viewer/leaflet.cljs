(ns viewer.leaflet
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :as debug]))

(defn create-marker [[lat lon]]
  (let [latLng (.latLng js/L lat lon)]
    (.marker js/L latLng #js {:clickable false})))

(defn create-point [[lat lon] attrs]
  (let [latLng (.latLng js/L lat lon)]
    (.circleMarker js/L latLng (clj->js (merge {:clickable false :radius 5}
                                               attrs)))))

(defn layer-type [layer-def]
  (first layer-def))

(defmulti leaflet-layer layer-type)

(defmethod leaflet-layer :default [layer-def]
  (throw (str "Unknown layer type " (first layer-def))))

(defmethod leaflet-layer :marker-layer [[_ props & children]]
  (let [layer (.layerGroup js/L)
        points (:points props)]
    (doseq [point points] (.addLayer layer (create-marker point)))
    layer))

(defmethod leaflet-layer :point-layer [[_ props & children]]
  (let [layer (.layerGroup js/L)
        points (:points props)
        attrs (dissoc props :points)]
    (doseq [point points] (.addLayer layer (create-point point attrs)))
    layer))

(defmethod leaflet-layer :geojson-layer [[_ props & children]]
  (let [layer (.geoJson js/L nil #js {:clickable false})
        data (:data props)]
    (when data (.addData layer data))
    layer))

(defmethod leaflet-layer :tile-layer [[_ props & children]]
  (let [url (:url props)
        attrs (dissoc props :url)
        layer (.tileLayer js/L url (clj->js attrs))]
    layer))

(defn leaflet-replace-layer [leaflet old-layer new-layer-def]
  (when old-layer (.removeLayer leaflet old-layer))
  (when-let [new-layer (when new-layer-def (leaflet-layer new-layer-def))]
    (.addLayer leaflet new-layer)
    new-layer))

(defn leaflet-update-layers [this]
  (let [state (reagent/state this)
        leaflet (:map state)
        new-children (reagent/children this)]

    ;; go through all the layers, old and new, and update the Leaflet objects
    ;; accordingly while updating the map
    (loop [old-children (:current-children state)
           old-layers (:layers state)
           children new-children
           layers []]
      (let [old-child (first old-children)
            old-layer (first old-layers)
            child (first children)]
        (if (or old-child child)
          ;; either there was a layer, or we have a new one
          (let [new-layer (if (= old-child child)
                            old-layer
                            (leaflet-replace-layer leaflet old-layer child))]
            ;; next layer
            (recur (rest old-children)
                   (rest old-layers)
                   (rest children)
                   (conj layers new-layer)))

          ;; finished looping over the layers
          (reagent/set-state this {:layers layers
                                   :current-children new-children}))))))

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
