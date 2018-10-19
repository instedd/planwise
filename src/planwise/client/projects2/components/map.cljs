(ns planwise.client.projects2.components.settings.map
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [leaflet.core :as l]
            [planwise.client.mapping :refer [static-image fullmap-region-geo] :as mapping]))


(defn- safe-component-mounted? [component]
  (try (boolean (r/dom-node component)) (catch js/Object _ false)))

(defn floating-div
  [{:keys [dispatch-fn map-class scroll-lower-bound] :as props} children]
  (let [listener-fn (atom nil)
        top-bound   (atom nil)
        low-bound   (atom scroll-lower-bound)
        detach-scroll-listener (fn []
                                 (when @listener-fn
                                   (.removeEventListener js/window "scroll" @listener-fn)
                                   (reset! listener-fn nil)))

        div-should-float? (fn [this]
                            (let [current-height (.-scrollY js/window)]
                              (< @top-bound
                                 current-height)))

        scroll-listener (fn [this]
                          (when (safe-component-mounted? this)
                            (let [extra-class (when (> (.-scrollY js/window) (.-innerHeight js/window)) " fixed-lower-bound")]
                              (dispatch-fn (str (get map-class (div-should-float? this)) extra-class)))))

        attach-scroll-listener (fn [this]
                                 (when-not @listener-fn
                                   (reset! listener-fn (partial scroll-listener this))
                                   (reset! top-bound (.-offsetTop (r/dom-node this)))
                                   (.addEventListener js/window "scroll" @listener-fn)))]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (attach-scroll-listener this))
      :component-did-update
      (fn [this _]
        (attach-scroll-listener this))
      :component-will-unmount
      detach-scroll-listener
      :reagent-render
      (fn [props children]
        (let [props (dissoc props :dispatch-fn :map-class :scroll-lower-bound)]
          [:div props children]))})))

(def classes {false "static" true "floating"})

(defn show-region-map
  [{:keys [bbox provider-set-id]}]
  (let [zoom      (r/atom 3)
        position  (r/atom mapping/map-preview-position)
        providers (rf/subscribe [:projects2/providers-layer])
        should-get-providers? (rf/subscribe [:projects2/should-get-providers?])
        class-name (rf/subscribe [:projects2/map-settings-class-name])]
    (fn [{:keys [bbox]}]
      (let [providers-layer [:marker-layer  {:points @providers
                                             :lat-fn #(:lat %)
                                             :lon-fn #(:lon %)
                                             :icon-fn (fn [p]
                                                        {:className
                                                         (str
                                                          (if (:disabled? p)
                                                            "leaflet-circle-icon-gray"
                                                            "leaflet-circle-icon-orange"))})}]]
        (when @should-get-providers? (rf/dispatch [:projects2/get-providers-for-project]))
        [floating-div
         (merge
          {:id "settings-map"
           :map-class classes
           :dispatch-fn #(rf/dispatch [:projects2/save-settings-map-class-name %])}
          (when-let [c @class-name] {:class-name c}))
         [l/map-widget {:zoom @zoom
                        :position @position
                        :on-position-changed #(reset! position %)
                        :on-zoom-changed #(reset! zoom %)
                        :controls []
                        :initial-bbox bbox}
          mapping/default-base-tile-layer
          providers-layer]]))))