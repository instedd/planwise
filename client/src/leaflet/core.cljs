(ns leaflet.core
  (:require [reagent.core :as reagent :refer [atom]]
            [leaflet.layers :as layers]
            [leaflet.controls :as controls]
            [planwise.client.utils :as utils]))


(defn- update-objects-from-decls
  "Updates a collection of (possibly mutable) objects by comparing seqs of
  declarations (which should correspond to those objects) and disposing/creating
  new ones when the declarations change. Returns a vector of objects conforming
  to new-decls."
  [old-objects
   old-decls
   new-decls
   update-fn!]

  (letfn [(replace-object [old-object old-decl new-decl]
            (if (= old-decl new-decl)
              ;; declarations are identical, nothing to replace
              old-object
              (update-fn! old-object new-decl old-decl)))]

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

(defn- leaflet-moveend-handler
  [this]
  (fn [e]
    (let [state               (reagent/state this)
          props               (reagent/props this)
          leaflet             (:map state)
          center              (.getCenter leaflet)
          new-pos             [(.-lat center) (.-lng center)]
          new-zoom            (.getZoom leaflet)
          current-pos         (:position state)
          current-zoom        (:zoom state)
          on-position-changed (:on-position-changed props)
          on-zoom-changed     (:on-zoom-changed props)]
      (when (and on-position-changed (not= new-pos current-pos))
        (on-position-changed new-pos))
      (when (and on-zoom-changed (not= new-zoom current-zoom))
        (on-zoom-changed new-zoom)))))

(defn- leaflet-update-layers
  [this]
  (let [state        (reagent/state this)
        leaflet      (:map state)
        new-children (reagent/children this)]

    ;; go through all the layers, old and new, and update the Leaflet objects
    ;; accordingly while updating the map
    (let [old-layers      (:layers state)
          old-children    (:current-children state)
          remove-layer-fn (fn [old-layer]
                            (.removeLayer leaflet old-layer))
          create-layer-fn (fn [new-child]
                            (let [new-layer (layers/create-layer new-child)]
                              (when new-layer (.addLayer leaflet new-layer))
                              new-layer))
          update-layer-fn (fn [old-layer new-child old-child]
                            (if-let [new-layer (and old-layer (layers/update-layer old-layer new-child old-child))]
                              new-layer
                              (do
                                (when old-layer (remove-layer-fn old-layer))
                                (when new-child (create-layer-fn new-child)))))
          new-layers      (update-objects-from-decls old-layers
                                                     old-children
                                                     new-children
                                                     update-layer-fn)]

      ;; check if the children marked as fit-bounds have changed
      ;; if so, update the leaflet map viewport
      (let [old-children-to-fit     (filter #(get-in % [1 :fit-bounds]) old-children)
            new-children-to-fit     (filter #(get-in % [1 :fit-bounds]) new-children)
            children-to-fit-changed (not= old-children-to-fit new-children-to-fit)
            any-children-to-fit     (seq new-children-to-fit)
            layers-to-fit           (vals (select-keys (zipmap new-children new-layers) new-children-to-fit))]
        (if (and children-to-fit-changed any-children-to-fit)
          (let [feature-group-to-fit (reduce #(.addLayer %1 %2) (.featureGroup js/L) layers-to-fit)
                bounds               (.getBounds feature-group-to-fit)]
            (.fitBounds leaflet bounds))))

      ;; update component state
      (reagent/set-state this {:layers           new-layers
                               :current-children new-children}))))

(defn- leaflet-update-viewport
  [this]
  (let [state    (reagent/state this)
        props    (reagent/props this)
        position (:position props)
        zoom     (:zoom props)
        leaflet  (:map state)]
    (if (or (not= position (:position state)) (not= zoom (:zoom state)))
      (do
        (reagent/set-state this {:position position :zoom zoom})
        (.setView leaflet (clj->js position) zoom)))))

(defn- leaflet-update-options
  [this]
  (let [state         (reagent/state this)
        props         (reagent/props this)
        max-bounds    (:max-bounds props)
        pointer-class (:pointer-class props)
        leaflet       (:map state)]
    ;pointer
    (if (not (empty? (:pointer-class state)))
      (.removeClass js/L.DomUtil (reagent/dom-node this) (:pointer-class state)))
    (if (not (empty? pointer-class))
      (.addClass js/L.DomUtil (reagent/dom-node this) pointer-class))
    (reagent/set-state this {:pointer-class pointer-class})
    ;max-bounds
    (when (not= max-bounds (:max-bounds state))
      (reagent/set-state this {:max-bounds max-bounds})
      (let [[[s w] [n e]]  max-bounds
            lat-lng-bounds (.latLngBounds js/L (.latLng js/L s w) (.latLng js/L n e))]
        (.setMaxBounds leaflet lat-lng-bounds)))))

(def default-controls [:zoom :attribution :legend])

(defn- leaflet-update-controls
  [this]
  (let [state            (reagent/state this)
        leaflet          (:map state)
        props            (reagent/props this)
        new-control-defs (or (:controls props) default-controls)]

    (let [old-controls       (:controls state)
          old-control-defs   (:current-controls state)
          destroy-control-fn (fn [old-control]
                               (.removeControl leaflet old-control))
          create-control-fn  (fn [new-control-def]
                               (let [new-control (controls/create-control new-control-def)]
                                 (.addControl leaflet new-control)
                                 new-control))
          update-control-fn  (fn [old-control new-control-def old-control-def]
                               (when old-control (destroy-control-fn old-control))
                               (when new-control-def (create-control-fn new-control-def)))
          new-controls       (update-objects-from-decls old-controls
                                                        old-control-defs
                                                        new-control-defs
                                                        update-control-fn)]
      (reagent/set-state this {:current-controls new-control-defs
                               :controls         new-controls}))))

(defn- leaflet-click-handler
  [this]
  (fn [e]
    (when-let [on-click (:on-click (reagent/props this))]
      (let [latlng        (.-latlng e)
            lat           (.-lat latlng)
            lon           (.-lng latlng)
            originalEvent (aget e "originalEvent")
            shiftKey      (aget originalEvent "shiftKey")]
        (on-click lat lon shiftKey)))))

(defn- leaflet-did-mount
  [this]
  (let [props        (reagent/props this)
        leaflet      (.map js/L (reagent/dom-node this)
                           #js {:zoomControl        false
                                :attributionControl false
                                :minZoom            (:min-zoom props)})
        initial-bbox (:initial-bbox props)]

    (reagent/set-state this {:map leaflet})

    (leaflet-update-controls this)
    (leaflet-update-layers this)
    (leaflet-update-options this)
    (leaflet-update-viewport this)

    (.on leaflet "moveend" (leaflet-moveend-handler this))
    (.on leaflet "click" (leaflet-click-handler this))

    (when (some? initial-bbox)
      (let [[[s w] [n e]]  initial-bbox
            lat-lng-bounds (.latLngBounds js/L (.latLng js/L s w) (.latLng js/L n e))]
        (.fitBounds leaflet lat-lng-bounds)))

    (when-let [map-ref (:ref props)]
      (map-ref leaflet))))

(defn- leaflet-will-unmount
  [this]
  (let [state   (reagent/state this)
        props   (reagent/props this)
        leaflet (:map state)]
    (when-let [map-ref (:ref props)]
      (map-ref nil))
    (.remove leaflet)))

(defn- leaflet-did-update
  [this old-argv]
  (let [state   (reagent/state this)
        leaflet (:map state)]

    (leaflet-update-layers this)
    (leaflet-update-options this)
    (leaflet-update-viewport this)))

(defn- size-style
  [width height]
  (into {} (map (fn [k v] (when v) [k (if (number? v) (str v "px") v)])
                [:width :height]
                [width height])))

(defn- leaflet-render
  [props & children]
  (let [height (:height props)
        width  (:width props)]
    [:div {:style (size-style width height)}]))

(defn map-widget
  [props]
  (reagent/create-class {:display-name           "leaflet/map-widget"
                         :reagent-render         leaflet-render
                         :component-did-mount    leaflet-did-mount
                         :component-will-unmount leaflet-will-unmount
                         :component-did-update   leaflet-did-update}))
