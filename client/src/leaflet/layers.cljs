(ns leaflet.layers)


(defn- create-marker
  [point {:keys [lat-fn lon-fn label-fn icon-fn popup-fn mouseover-fn mouseout-fn]
          :or   {lat-fn :lat lon-fn :lon}
          :as   props}]
  (let [latLng (.latLng js/L (lat-fn point) (lon-fn point))
        attrs  (dissoc props :lat-fn :lon-fn :icon-fn :popup-fn)
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
      ;; Delay popup until the layer is added to the map
      (do
        (js/setTimeout #(.openPopup marker) 20)))
    marker))

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


(defmulti create-layer (fn [layer-def] (first layer-def)))

(defmulti update-layer
  (fn [instance layer-def old-layer-def]
    ;; layers can only be updated if the layer type has not changed
    (if (= (first layer-def) (first old-layer-def))
      (first layer-def)
      ::not-updatable))
  :default ::not-updatable)

(defmethod create-layer :default
  [layer-def]
  (throw (ex-info "Unknown layer type" layer-def)))

(defmethod update-layer ::not-updatable
  [instance layer-def old-layer-def]
  ;; update is not possible by default
  nil)

(defmethod create-layer :marker-layer
  [[_ props & children]]
  (let [layer  (.featureGroup js/L)
        points (:points props)
        attrs  (dissoc props :points)]
    (doseq [point points] (.addLayer layer (create-marker point attrs)))
    layer))

(defmethod update-layer :marker-layer
  [instance [_ props & children] _]
  (.clearLayers instance)
  (let [points (:points props)
        attrs  (dissoc props :points)]
    (doseq [point points] (.addLayer instance (create-marker point attrs)))
    instance))

(defmethod create-layer :geojson-layer
  [[_ props & children]]
  (let [data  (:data props)
        layer (geojson-layer props)]
    (when data
      (.addData layer (js-data data)))
    layer))

(defmethod update-layer :geojson-layer
  [instance [_ props & children] _]
  (.clearLayers instance)
  (some->> (:data props)
           js-data
           (.addData instance))
  instance)

(defmethod create-layer :tile-layer
  [[_ props & children]]
  (let [url   (:url props)
        attrs (dissoc props :url)
        layer (.tileLayer js/L url (clj->js attrs))]
    layer))

(defmethod create-layer :wms-tile-layer
  [[_ props & children]]
  (let [url   (:url props)
        attrs (dissoc props :url)
        layer (when (:layers props) (js/L.tileLayer.wms url (clj->js attrs)))]
    layer))

(def ^:private options-keys [:opacity])

(defmethod update-layer :wms-tile-layer
  [instance [_ props & children] [_ old-props & children]]
  (let [options     (select-keys props options-keys)
        old-options (select-keys old-props options-keys)]
    (when (= options old-options)
      (let [params     (apply dissoc props :url options-keys)
            old-params (apply dissoc old-props :url options-keys)
            url        (:url props)
            old-url    (:url old-props)]
        (when-not (= url old-url) (.setUrl instance url))
        (when-not (= params old-params) (.setParams instance (clj->js params)))
        instance))))
