(ns planwise.client.playground.views
  (:require [leaflet.core :refer [map-widget]]
            [planwise.client.slider :refer [threshold-slider]]
            [planwise.client.hud :refer [coords-and-info]]
            [re-frame.core :refer [subscribe dispatch]]))

(def mapbox-tile-url "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}")
(def mapbox-mapid "ggiraldez.056e1919")
(def mapbox-access-token "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA")

(defn playground-page []
  (let [playground (subscribe [:playground])]
    (fn []
      (let [view (:map-view @playground)
            points (:points @playground)
            isochrone (:isochrone @playground)
            facilities (:facilities @playground)
            threshold (:threshold @playground)
            node-id (:node-id @playground)
            geojson (:geojson @playground)
            position (:position view)
            zoom (:zoom view)]
        [:div
         [threshold-slider {:value threshold
                            :on-change #(dispatch [:playground/update-threshold %])}]
         [map-widget {:height 650
                      :position position
                      :zoom zoom
                      :on-click
                      #(dispatch [:playground/map-clicked %1 %2 %3])
                      :on-position-changed
                      #(dispatch [:playground/update-position %])
                      :on-zoom-changed
                      #(dispatch [:playground/update-zoom %])}
          [:tile-layer {:url mapbox-tile-url
                        :attribution "&copy; Mapbox"
                        :maxZoom 18
                        :mapid mapbox-mapid
                        :accessToken mapbox-access-token}]
          [:point-layer {:points (map (fn [fac] [(fac "lat") (fac "lon")]) facilities)
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
          [:marker-layer {:points points}]]
         [coords-and-info {:lat (first position)
                           :lon (second position)
                           :zoom zoom
                           :node-id node-id
                           :on-reset-view
                           #(dispatch [:playground/reset-view])
                           :on-load-geojson
                           #(dispatch [:playground/load-geojson])}]]))))
