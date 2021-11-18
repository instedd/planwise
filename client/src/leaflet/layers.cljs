(ns leaflet.layers)


(defn- create-marker
  [point {:keys [lat-fn lon-fn label-fn style-fn icon-fn popup-fn mouseover-fn mouseout-fn]
          :or   {lat-fn :lat lon-fn :lon}
          :as   props}]
  (let [latLng (.latLng js/L (lat-fn point) (lon-fn point))
        attrs  (dissoc props :lat-fn :lon-fn :popup-fn)
        icon   (if icon-fn
                 (.divIcon js/L (clj->js (icon-fn point)))
                 (js/L.Icon.Default.))
        attrs  {:keyword false
                :icon    icon}
        marker (.marker js/L latLng (clj->js attrs))]
    (when label-fn
      (.bindTooltip marker (label-fn point) (clj->js {:permanent false})))
    (if popup-fn
      (.bindPopup marker (popup-fn point)))
    (.on marker "mouseover"  (fn [_]
                               (.openTooltip marker)
                               (when mouseover-fn (mouseover-fn point))))
    (.on marker "mouseout"   (fn [_]
                               (.closeTooltip marker)
                               (when (and mouseout-fn (not (.isPopupOpen marker)))
                                 (mouseout-fn point))))
    (.on marker "popupopen"  (fn [_]
                               ;; "hide" the tooltip while the popup is open
                               (when-let [tooltip (.getTooltip marker)]
                                 (.setOpacity tooltip 0))
                               (.closeTooltip marker)))
    (.on marker "popupclose" (fn [_]
                               ;; "restore" the tooltip while once the popup is closed
                               (when-let [tooltip (.getTooltip marker)]
                                 (.setOpacity tooltip 100))
                               (when mouseout-fn (mouseout-fn point))))
    (if (:open? point)
      ;; Delay popup until the layer was created
      (js/setTimeout #(when-not (.isPopupOpen marker)
                        (.openPopup marker)) 100))
    marker))

(defn- create-point
  [point {:keys [lat-fn lon-fn style-fn popup-fn mouseover-fn mouseout-fn]
          :or   {lat-fn :lat, lon-fn :lon}
          :as   props}]
  (let [latLng    (.latLng js/L (lat-fn point) (lon-fn point))
        attrs     (dissoc props :lat-fn :lon-fn :popup-fn)
        clickable (boolean popup-fn)
        style     (merge
                   {:clickable clickable :radius 5}
                   attrs
                   (when style-fn (style-fn point)))
        marker    (.circleMarker js/L latLng (clj->js style))]
    (if popup-fn
      (.bindPopup marker (popup-fn point)))
    (if mouseover-fn
      (.on marker "mouseover" #(mouseover-fn point)))
    (if mouseout-fn
      (do
        (.on marker "mouseout" #(when-not (.isPopupOpen marker) (mouseout-fn point)))
        (.on marker "popupclose" #(mouseout-fn point))))
    marker))

(defn- create-polygon
  [points {:keys [lat-fn lon-fn style-fn popup-fn]
           :or   {lat-fn :lat lon-fn :lon}
           :as   props}]
  (let [attrs     (dissoc props :lat-fn :lon-fn :popup-fn)
        clickable (boolean popup-fn)
        style     (merge
                   {:clickable clickable :radius 5}
                   attrs
                   (when style-fn (map style-fn points)))
        latLngs   (clj->js (map #(.latLng js/L (lat-fn %) (lon-fn %)) points))
        polygon   (.polygon js/L latLngs (clj->js style))]
    polygon))

(defn- js-data
  [data]
  (cond
    (string? data) (js/JSON.parse data)
    (vector? data) (clj->js (mapv #(if (string? %) (js/JSON.parse %) %) data))
    :else          data))

(defn- geojson-layer
  [props]
  (let [attrs       (dissoc props :data :fit-bounds)
        group-attrs (:group props)
        renderer    (.groupRenderer js/L.SVG (clj->js group-attrs))]
    (.geoJson js/L nil #js {:clickable false
                            :renderer  renderer
                            :style     (constantly (clj->js attrs))})))


(defmulti leaflet-layer (fn [layer-def] (first layer-def)))

(defmethod leaflet-layer :default
  [layer-def]
  (throw (str "Unknown layer type " (first layer-def))))

(defmethod leaflet-layer :marker-layer
  [[_ props & children]]
  (let [layer  (.featureGroup js/L)
        points (:points props)
        attrs  (dissoc props :points)]
    (doseq [point points] (.addLayer layer (create-marker point attrs)))
    layer))

(defmethod leaflet-layer :point-layer
  [[_ props & children]]
  (let [layer  (.layerGroup js/L)
        points (:points props)
        attrs  (dissoc props :points)]
    (doseq [point points] (.addLayer layer (create-point point attrs)))
    layer))

(defmethod leaflet-layer :polygon-layer
  [[_ props & children]]
  (let [layer    (.layerGroup js/L)
        polygons (:polygons props)
        attrs    (dissoc props :polygons)]
    (doseq [points polygons] (.addLayer layer (create-polygon points attrs)))
    layer))

(defmethod leaflet-layer :geojson-layer
  [[_ props & children]]
  (let [data  (:data props)
        layer (geojson-layer props)]
    (when data
      (.addData layer (js-data data)))
    layer))

(defmethod leaflet-layer :tile-layer
  [[_ props & children]]
  (let [url   (:url props)
        attrs (dissoc props :url)
        layer (.tileLayer js/L url (clj->js attrs))]
    layer))

(defmethod leaflet-layer :wms-tile-layer
  [[_ props & children]]
  (let [url   (:url props)
        attrs (dissoc props :url)
        layer (when (:layers props) (js/L.tileLayer.wms url (clj->js attrs)))]
    layer))
