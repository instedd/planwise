(ns leaflet.layers
  (:require [crate.core :as crate]
            [goog.object]))


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

;; sets a JS object properties from a Clojure map, effectively updating the target object
(defn- merge-props
  [target props]
  (doseq [[key value] props]
    (goog.object/set target (name key) (clj->js value))))


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
                                    :className "mdc-typography"
                                    :opacity   1
                                    :direction "top"
                                    :offset    tooltip-offset})

(defn- deactivate-marker
  [marker]
  (.off marker "click")
  (.off marker "mouseover")
  (.off marker "mouseout")
  (.unbindTooltip marker))

(defn- activate-marker
  [marker]
  (let [{:keys [tooltip hover? click-fn mouseover-fn mouseout-fn] :as props} (.-__props marker)]
    (when tooltip
      (let [content (cond
                      (vector? tooltip)                  (crate/html tooltip)
                      (string? tooltip)                  tooltip
                      (instance? js/HTMLElement tooltip) tooltip
                      :else                              (str tooltip))]
        (.bindTooltip marker content tooltip-options)))
    (when click-fn (.on marker "click" #(click-fn props)))
    (when mouseover-fn (.on marker "mouseover" #(mouseover-fn props)))
    (when mouseout-fn  (.on marker "mouseout"  #(mouseout-fn props)))
    (if hover?
      (.openTooltip marker)
      (.closeTooltip marker))))

(defmethod create-layer :marker
  [[_ {:keys [lat lon icon tooltip click-fn mouseover-fn mouseout-fn] :as props}]]
  (let [latLng (.latLng js/L lat lon)
        icon   (if icon
                 (.divIcon js/L (clj->js icon))
                 (js/L.Icon.Default.))
        attrs  (-> props
                   (assoc :riseOnHover true)
                   (assoc :icon icon)
                   (dissoc :lat :lon :tooltip :click-fn :mouseover-fn :mouseout-fn))
        marker (.marker js/L latLng (clj->js attrs))]
    (set! (.-__props marker) props)
    (activate-marker marker)
    (when (and tooltip (:hover? props))
      (.on marker "add" #(.openTooltip marker)))
    marker))

(defmethod update-layer :marker
  [marker [_ {:keys [lat lon icon tooltip opacity zIndexOffset] :as props}] [_ old-props]]
  (merge-props (.-options marker)
               (dissoc props :lat :lon :tooltip :icon :opacity :mouseover-fn :mouseout-fn :zIndexOffset))
  (when (not= zIndexOffset (:zIndexOffset old-props))
    (.setZIndexOffset marker zIndexOffset))
  (when (not= [lat lon] [(:lat old-props) (:lon old-props)])
    (.setLatLng marker (.latLng js/L lat lon)))
  (when (not= icon (:icon old-props))
    (.setIcon marker (if icon
                       (.divIcon js/L (clj->js icon))
                       (js/L.Icon.Default.))))
  (when (not= opacity (:opacity old-props))
    (.setOpacity marker opacity))
  (deactivate-marker marker)
  (set! (.-__props marker) props)
  (activate-marker marker)
  marker)


;; LayerGroup children handling

(defn- add-children-to-layer!
  [layer children]
  (let [child-layers (mapv create-layer children)]
    (if (.-addLayers layer)
      (.addLayers layer (clj->js child-layers))
      (doseq [child-layer child-layers]
        (.addLayer layer child-layer)))
    (set! (.-__children layer) children)
    (set! (.-__layers layer) child-layers)
    layer))

(defn- update-layer-children!
  [layer children]
  (let [old-children (.-__children layer)
        old-layers   (.-__layers layer)
        new-layers   (loop [old-layers   old-layers
                            old-children old-children
                            new-layers   []
                            new-children children]
                       (if (and (empty? old-children) (empty? new-children))
                         new-layers
                         (let [old-child (first old-children)
                               new-child (first new-children)
                               old-layer (first old-layers)
                               new-layer (cond
                                           (= old-child new-child)
                                           old-layer

                                           (nil? new-child)
                                           (do (.removeLayer layer old-layer) nil)

                                           (nil? old-child)
                                           (let [new-layer (create-layer new-child)]
                                             (.addLayer layer new-layer)
                                             new-layer)

                                           :else
                                           (if (update-layer old-layer new-child old-child)
                                             old-layer
                                             (let [new-layer (create-layer new-child)]
                                               (.removeLayer layer old-layer)
                                               (.addLayer layer new-layer)
                                               new-layer)))]

                           (recur (rest old-layers)
                                  (rest old-children)
                                  (conj new-layers new-layer)
                                  (rest new-children)))))]
    (set! (.-__children layer) children)
    (set! (.-__layers layer) new-layers)
    layer))

(defmethod create-layer :feature-group
  [[_ props & children]]
  (let [layer (.featureGroup js/L (clj->js props))]
    (add-children-to-layer! layer children)))

(defmethod update-layer :feature-group
  [instance [_ props & children] _]
  (update-layer-children! instance children))

(defn- spiderfy-cluster
  [cluster]
  ;; deactivate markers in cluster to avoid mouseover events/tooltips while the animation is running
  (doseq [marker (.getAllChildMarkers (.-layer cluster))]
    (deactivate-marker marker))
  (.spiderfy (. cluster -layer)))

(defn- cluster-spiderfied
  [event]
  ;; re-activate markers after they are fully spiderfied
  (doseq [marker (.-markers event)]
    (activate-marker marker)))

;; TODO(gus): the cluster-icon-fn interface can be improved; in particular we
;; may want to pass the markers props to the function instead of the cluster
;; layer to avoid having the client code tamper with Leaflet internal functions
(defmethod create-layer :cluster-group
  [[_ {:keys [cluster-icon-fn] :as props} & children]]
  (let [props (cond-> props
                :always         (-> (dissoc :cluster-click-fn :cluster-icon-fn)
                                    (assoc :showCoverageOnHover false
                                           :zoomToBoundsOnClick false
                                           :maxClusterRadius 50))
                cluster-icon-fn (assoc :iconCreateFunction #(.divIcon js/L (clj->js (cluster-icon-fn %)))))
        layer (.markerClusterGroup js/L (clj->js props))]

    (.on layer "clusterclick" spiderfy-cluster)
    (.on layer "spiderfied" cluster-spiderfied)

    (add-children-to-layer! layer children)))

(defmethod update-layer :cluster-group
  [instance [_ {:keys [cluster-icon-fn] :as props} & children] _]
  (let [icon-create-function (when cluster-icon-fn #(.divIcon js/L (clj->js (cluster-icon-fn %))))]
    (set! (.-iconCreateFunction (.-options instance)) icon-create-function))
  (update-layer-children! instance children)
  (.refreshClusters instance)
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
