(ns planwise.client.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [leaflet.core :refer [map-widget]]
            [planwise.client.slider :refer [threshold-slider]]
            [planwise.client.hud :refer [coords-and-info]]
            [planwise.client.state :as state :refer [app]]))

;; -------------------------
;; Views

(defn home-page []
  (fn []
    (let [app-state @app
          position (:position app-state)
          zoom (:zoom app-state)
          points (:points app-state)
          geojson (:geojson app-state)
          isochrone (:isochrone app-state)
          threshold (:threshold app-state)
          facilities (:facilities app-state)
          node-id (:node-id app-state)]
      [:div
       [threshold-slider {:value threshold
                          :on-change state/update-threshold}]
       [map-widget {:height 700
                    :position position
                    :zoom zoom
                    :on-click (fn [lat lon modifier?]
                                (state/fetch-nearest-node lat lon modifier?))
                    :on-position-changed state/update-position
                    :on-zoom-changed state/update-zoom}
        [:tile-layer {:url "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}"
                      :attribution "&copy; Mapbox"
                      :maxZoom 18
                      :mapid "ggiraldez.056e1919"
                      :accessToken "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA"}]
        [:point-layer {:points (map (fn [fac] [(fac "lat") (fac "lon")]) facilities)
                       :radius 3
                       :color "#f00"
                       :opacity 0.3
                       :fillOpacity 0.3}]
        [:geojson-layer {:data geojson
                         :color "#f00"
                         :opacity 0.2
                         :weight 2}]
        [:marker-layer {:points points}]
        [:geojson-layer {:data isochrone
                         :weight 3
                         :color "#00f"}]]
       [coords-and-info {:lat (first position)
                         :lon (second position)
                         :zoom zoom
                         :node-id node-id
                         :on-reset-view state/reset-viewport}]])))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (state/init!)
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))

