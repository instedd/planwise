(ns leaflet.layers)


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

(def ^:private tooltip-offset (js/L.Point. 0 -15))
(def ^:private tooltip-options #js {:permanent false
                                    :direction "top"
                                    :offset    tooltip-offset})

(def ^:private popup-offset (js/L.Point. 0 -10))
(def ^:private popup-padding-top-left (js/L.Point. 600 50))
(def ^:private popup-padding-bottom-right (js/L.Point. 200 50))
(def ^:private popup-options #js {:offset                    popup-offset
                                  :autoPanPaddingTopLeft     popup-padding-top-left
                                  :autoPanPaddingBottomRight popup-padding-bottom-right})

(defmethod create-layer :marker
  [[_ {:keys [lat lon icon tooltip popup-fn mouseover-fn mouseout-fn] :as props}]]
  (let [latLng        (.latLng js/L lat lon)
        icon          (if icon
                        (.divIcon js/L (clj->js icon))
                        (js/L.Icon.Default.))
        attrs         (-> props
                          (assoc :riseOnHover true)
                          (assoc :icon icon)
                          (dissoc :lat :lon :tooltip :popup-fn))
        marker        (.marker js/L latLng (clj->js attrs))]
    (when tooltip
      (.bindTooltip marker tooltip tooltip-options))
    (when popup-fn
      ;; lazy-load the popup content
      (.bindPopup marker "" popup-options)
      (.on marker "popupopen" (fn [e] (.setContent (.-popup e) (popup-fn props)))))
    (.on marker "mouseover"  (fn [e]
                               (when-not (.isPopupOpen marker)
                                 (.bindPopup marker (popup-fn props) popup-options))
                               (when mouseover-fn (mouseover-fn props))))
    (.on marker "mouseout"   (when mouseout-fn #(when-not (.isPopupOpen marker)
                                                  (mouseout-fn props))))
    (.on marker "popupopen"  (fn [_]
                               ;; "hide" the tooltip while the popup is open
                               (some-> (.getTooltip marker) (.setOpacity 0))
                               (.closeTooltip marker)))
    (.on marker "popupclose" (fn [_]
                               ;; "restore" the tooltip while once the popup is closed
                               (some-> (.getTooltip marker) (.setOpacity 100))
                               (when mouseout-fn (mouseout-fn props))))
    (when (:open? props)
      (.on marker "add" #(.openPopup marker)))
    (when (and tooltip (:hover? props))
      (.on marker "add" #(.openTooltip marker)))
    marker))

(defmethod create-layer :feature-group
  [[_ props & children]]
  (let [layer (.featureGroup js/L (clj->js props))]
    (doseq [child children]
      (.addLayer layer (create-layer child)))
    layer))

(defmethod update-layer :feature-group
  [instance [_ props & children] _]
  (.clearLayers instance)
  (doseq [child children]
    (.addLayer instance (create-layer child)))
  instance)

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
