(ns viewer.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [viewer.leaflet :refer [map-widget]]
            [viewer.slider :refer [threshold-slider]]
            [viewer.hud :refer [coords-and-info]]
            [viewer.state :as state :refer [app]]))

;; -------------------------
;; Views

(defn home-page []
  (fn []
    (let [position (:position @app)
          zoom (:zoom @app)
          points (:points @app)
          geojson (:geojson @app)
          threshold (:threshold @app)
          node-id (:node-id @app)]
      [:div
       [threshold-slider {:value threshold
                          :on-change state/update-threshold}]
       [map-widget {:position position
                    :zoom zoom
                    :points points
                    :geojson geojson
                    :on-click state/fetch-nearest-node
                    :on-position-changed state/update-position
                    :on-zoom-changed state/update-zoom}]
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
