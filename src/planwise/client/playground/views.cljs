(ns planwise.client.playground.views
  (:require [leaflet.core :refer [map-widget]]
            [planwise.client.mapping :refer [default-base-tile-layer]]
            [planwise.client.components.slider :refer [threshold-slider decimal-slider]]
            [planwise.client.components.hud :refer [coords-and-info]]
            [re-frame.core :refer [subscribe dispatch]]))

(defn playground-page []
  (let [playground (subscribe [:playground])]
    (fn []
      (let [view (:map-view @playground)
            points (:points @playground)
            isochrone (:isochrone @playground)
            facilities (:facilities @playground)
            threshold (:threshold @playground)
            simplify (:simplify @playground)
            algorithm (:algorithm @playground)
            node-id (:node-id @playground)
            geojson (:geojson @playground)
            position (:position view)
            zoom (:zoom view)
            isodata (:isochrones @playground)
            num-points (:num-points @playground)]

        [:article.playground
         [:div.header
          [threshold-slider {:value threshold
                             :on-change #(dispatch [:playground/update-threshold %])}]
          [decimal-slider {:value simplify
                           :on-change #(dispatch [:playground/update-simplify %])}]
          [:div
           [:select {:on-change #(dispatch [:playground/update-algorithm (-> % .-target .-value)])
                     :value algorithm}
            (map (fn [val] [:option {:value val :key val} val]) ["alpha-shape" "buffer"])]]]
         [:div.body
          [map-widget {:position position
                       :zoom zoom
                       :on-click
                       #(dispatch [:playground/map-clicked %1 %2 %3])
                       :on-position-changed
                       #(dispatch [:playground/update-position %])
                       :on-zoom-changed
                       #(dispatch [:playground/update-zoom %])}
           default-base-tile-layer
           [:point-layer {:points (map (fn [fac] [(fac :lat) (fac :lon)]) facilities)
                          :radius 3
                          :color "#f00"
                          :opacity 0.3
                          :fillOpacity 0.3}]
           [:geojson-layer {:data geojson
                            :color "#f00"
                            :opacity 0.2
                            :weight 2}]
           [:geojson-layer {:data isochrone
                            :weight 3
                            :color "#00f"}]
           [:geojson-layer {:data isodata
                            :color "#0f0"
                            :weight 3
                            :fillOpacity 0.2
                            :opacity 0.5}]
           [:marker-layer {:points points
                           :lat-fn first
                           :lon-fn last}]]]
         [:div.footer
          [coords-and-info {:lat (first position)
                            :lon (second position)
                            :zoom zoom
                            :node-id node-id
                            :num-points num-points
                            :on-reset-view
                            #(dispatch [:playground/reset-view])
                            :on-load-geojson
                            #(dispatch [:playground/load-geojson])}]]]))))
