(ns leaflet.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :as debug]
            [clojure.data :as data]))


(defn update-objects-from-decls
  "Updates a collection of (possibly mutable) objects by comparing seqs of
  declarations (which should correspond to those objects) and disposing/creating
  new ones when the declarations change. Returns a vector of objects conforming
  to new-decls."
  [old-objects
   old-decls
   new-decls
   dispose-fn!
   create-fn!]

  (letfn [(replace-object [old-object old-decl new-decl]
            (if (= old-decl new-decl)
              ;; declarations are identical, nothing to replace
              old-object
              (do
                ;; dispose of the old object and return the new one
                (when old-object (dispose-fn! old-object))
                (let [new-object (when new-decl (create-fn! new-decl))]
                  new-object))))]

    (loop [old-objects old-objects
           old-decls old-decls
           new-objects []
           new-decls new-decls]

      (if (and (empty? old-decls) (empty? new-decls))
        ;; done iterating over the declarations
        new-objects

        ;; replace declaration
        (let [old-decl (first old-decls)
              new-decl (first new-decls)]
          (let [old-object (first old-objects)
                new-object (replace-object old-object old-decl new-decl)]

            ;; next declaration
            (recur (rest old-objects)
                   (rest old-decls)
                   (conj new-objects new-object)
                   (rest new-decls))))))))

(defn create-marker [point {:keys [lat-fn lon-fn icon-fn popup-fn], :or {lat-fn :lat lon-fn :lon}}]
  (let [latLng (.latLng js/L (lat-fn point) (lon-fn point))
        icon   (if icon-fn
                 (.divIcon js/L #js {:className (icon-fn point)})
                 (js/L.Icon.Default.))
        marker (.marker js/L latLng #js {:clickable true
                                         :keyboard false
                                         :icon icon})]
    (if popup-fn
      (.bindPopup marker (popup-fn point))
      marker)))

(defn create-point [point {:keys [lat-fn lon-fn style-fn popup-fn], :or {lat-fn :lat, lon-fn :lon}, :as props}]
  (let [latLng    (.latLng js/L (lat-fn point) (lon-fn point))
        attrs     (dissoc props :lat-fn :lon-fn :popup-fn)
        clickable (boolean popup-fn)
        style     (merge
                    {:clickable clickable :radius 5}
                    attrs
                    (when style-fn (style-fn point)))
        marker    (.circleMarker js/L latLng (clj->js style))]
    (if popup-fn
      (.bindPopup marker (popup-fn point))
      marker)))

(defn layer-type [layer-def]
  (first layer-def))

(defn js-data [data]
  (cond
    (string? data) (js/JSON.parse data)
    (vector? data) (clj->js (mapv #(if (string? %) (js/JSON.parse %) %) data))
    :else data))

(defn geojson-layer [props]
  (let [attrs (dissoc props :data :fit-bounds)
        group-attrs (:group props)]
    (.group js/L.geoJson nil #js {:clickable false
                                  :pathGroup (clj->js group-attrs)
                                  :style (constantly (clj->js attrs))})))

(defmulti leaflet-layer layer-type)

(defmethod leaflet-layer :default [layer-def]
  (throw (str "Unknown layer type " (first layer-def))))

(defmethod leaflet-layer :marker-layer [[_ props & children]]
  (let [layer (.layerGroup js/L)
        points (:points props)
        attrs (select-keys props [:lat-fn :lon-fn :icon-fn :popup-fn])]
    (doseq [point points] (.addLayer layer (create-marker point attrs)))
    layer))

(defmethod leaflet-layer :point-layer [[_ props & children]]
  (let [layer  (.withPathGroup js/L.layerGroup)
        points (:points props)
        attrs  (dissoc props :points)]
    (doseq [point points] (.addLayer layer (create-point point attrs)))
    layer))

(defmethod leaflet-layer :geojson-layer [[_ props & children]]
  (let [data  (:data props)
        layer (geojson-layer props)]
    (when data
      (.addData layer (js-data data)))
    layer))

(defmethod leaflet-layer :geojson-bbox-layer [[_ props & children]]
  (let [attrs (dissoc props :data :fit-bounds)
        geojson-layer (geojson-layer attrs)
        bbox-loader   (.bboxLoader js/L (clj->js (assoc attrs :layer geojson-layer)))]
    (when-let [data (:data props)]
      (doseq [{:keys [facility-id level isochrone]} data]
        (let [layer (.asLayers geojson-layer (js/JSON.parse isochrone))]
          (.addFeature bbox-loader level facility-id layer))))
    bbox-loader))

(defmethod leaflet-layer :tile-layer [[_ props & children]]
  (let [url   (:url props)
        attrs (dissoc props :url)
        layer (.tileLayer js/L url (clj->js attrs))]
    layer))

(defmethod leaflet-layer :wms-tile-layer [[_ props & children]]
  (let [url   (:url props)
        attrs (dissoc props :url)
        layer (js/L.tileLayer.wms url (clj->js attrs))]
    layer))

(defn leaflet-replace-layer [leaflet old-layer new-layer-def]
  (when old-layer (.removeLayer leaflet old-layer))
  (when-let [new-layer (when new-layer-def (leaflet-layer new-layer-def))]
    (.addLayer leaflet new-layer)
    new-layer))

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

(defn leaflet-update-layers [this]
  (let [state (reagent/state this)
        leaflet (:map state)
        new-children (reagent/children this)]

    ;; go through all the layers, old and new, and update the Leaflet objects
    ;; accordingly while updating the map
    (let [old-layers (:layers state)
          old-children (:current-children state)
          remove-layer-fn (fn [old-layer]
                            (.removeLayer leaflet old-layer))
          create-layer-fn (fn [new-child]
                            (let [new-layer (leaflet-layer new-child)]
                              (.addLayer leaflet new-layer)
                              new-layer))
          new-layers (update-objects-from-decls old-layers
                                                old-children
                                                new-children
                                                remove-layer-fn
                                                create-layer-fn)]

      ;; check if the children marked as fit-bounds have changed
      ;; if so, update the leaflet map viewport
      (let [old-children-to-fit (filter #(get-in % [1 :fit-bounds]) old-children)
            new-children-to-fit (filter #(get-in % [1 :fit-bounds]) new-children)
            children-to-fit-changed (not= old-children-to-fit new-children-to-fit)
            any-children-to-fit (seq new-children-to-fit)
            layers-to-fit (vals (select-keys (zipmap new-children new-layers) new-children-to-fit))]
        (if (and children-to-fit-changed any-children-to-fit)
          (let [feature-group-to-fit (reduce #(.addLayer %1 %2) (.featureGroup js/L) layers-to-fit)
                bounds (.getBounds feature-group-to-fit)]
            (.fitBounds leaflet bounds)
            (leaflet-moveend-handler this))))

      ;; update component state
      (reagent/set-state this {:layers new-layers
                               :current-children new-children}))))

(defn leaflet-update-viewport [this]
  (let [state (reagent/state this)
        props (reagent/props this)
        position (:position props)
        zoom (:zoom props)
        leaflet (:map state)]
    (reagent/set-state this {:position position :zoom zoom})
    (.setView leaflet (clj->js position) zoom)))

(defn leaflet-update-options [this]
  (let [state (reagent/state this)
        props (reagent/props this)
        max-bounds (:max-bounds props)
        leaflet (:map state)]
    (when (not= max-bounds (:max-bounds state))
      (reagent/set-state this {:max-bounds max-bounds})
      (let [[[s w] [n e]] max-bounds
            lat-lng-bounds (.latLngBounds js/L (.latLng js/L s w) (.latLng js/L n e))]
        (.setMaxBounds leaflet lat-lng-bounds)))))

(defn leaflet-create-control [type props]
  (condp = type
    :zoom (.zoom js/L.control)
    :attribution (.attribution js/L.control #js {:prefix false})
    :legend (.legend js/L.control #js {:maxPopulation (:max-population props)} )
    (throw (str "Invalid control type " type))))

(def default-controls [:zoom :attribution :legend])

(defn leaflet-update-controls [this]
  (let [state (reagent/state this)
        leaflet (:map state)
        props (reagent/props this)
        new-control-defs (or (:controls props) default-controls)]

    (let [old-controls (:controls state)
          old-control-defs (:current-controls state)
          destroy-control-fn (fn [old-control]
                               (.removeControl leaflet old-control))
          create-control-fn (fn [new-control-def]
                              (let [new-control (leaflet-create-control new-control-def props)]
                                (.addControl leaflet new-control)
                                new-control))
          new-controls (update-objects-from-decls old-controls
                                                  old-control-defs
                                                  new-control-defs
                                                  destroy-control-fn
                                                  create-control-fn)]
      (reagent/set-state this {:current-controls new-control-defs
                               :controls new-controls}))))

(defn leaflet-click-handler [this]
  (fn [e]
    (when-let [on-click (:on-click (reagent/props this))]
      (let [latlng (.-latlng e)
            lat (.-lat latlng)
            lon (.-lng latlng)
            originalEvent (aget e "originalEvent")
            shiftKey (aget originalEvent "shiftKey")]
        (on-click lat lon shiftKey)))))

(defn leaflet-did-mount [this]
  (let [props (reagent/props this)
        leaflet (.map js/L (reagent/dom-node this)
                      #js {:zoomControl false
                           :attributionControl false
                           :minZoom (:min-zoom props)})]

    (reagent/set-state this {:map leaflet})

    (leaflet-update-controls this)
    (leaflet-update-layers this)
    (leaflet-update-options this)
    (leaflet-update-viewport this)

    (.on leaflet "moveend" (leaflet-moveend-handler this))
    (.on leaflet "click" (leaflet-click-handler this))))

(defn leaflet-will-unmount [this]
  (let [state (reagent/state this)
        leaflet (:map state)]
    (.remove leaflet)))

(defn leaflet-did-update [this old-argv]
  (let [state (reagent/state this)
        leaflet (:map state)]

    (leaflet-update-layers this)
    (leaflet-update-options this)
    (leaflet-update-viewport this)))

(defn size-style [width height]
  (into {} (map (fn [k v] (when v) [k (if (number? v) (str v "px") v)])
                [:width :height]
                [width height])))

(defn leaflet-render [props & children]
  (let [height (:height props)
        width (:width props)]
    [:div {:style (size-style width height)}]))

(defn map-widget [props]
  (reagent/create-class {:display-name "leaflet/map-widget"
                         :reagent-render leaflet-render
                         :component-did-mount leaflet-did-mount
                         :component-will-unmount leaflet-will-unmount
                         :component-did-update leaflet-did-update}))
