(ns viewer.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [goog.string :as gstring]
              [goog.string.format]
              [ajax.core :refer [GET POST]]
              [clojure.string :as string]
              [viewer.leaflet :as leaflet]))

(def initial-app-state {:position [-1.29 36.83]
                        :zoom 11
                        :points []
                        :geojson nil})

(defonce app (atom initial-app-state))

(defn format-coord [x]
  (gstring/format "%.4f" x))

(defn parse-points-csv [csv]
  (let [lines (string/split-lines csv)]
    (->> lines
         (filter #(not= \# (first %)))
         (map #(mapv js/parseFloat (string/split % #","))))))

(defn on-points-data [data]
  (let [points (parse-points-csv data)]
    (swap! app assoc-in [:points] points)))

(defn fetch-points []
  (GET "/data/data.csv" {:handler on-points-data}))

(defn on-geojson-data [data]
  (let [geojson (.parse js/JSON data)]
    (swap! app assoc-in [:geojson] geojson)))

(defn fetch-geojson []
  (GET "/data/poly.geojson" {:handler on-geojson-data}))

;; -------------------------
;; Views

(defn home-page []
  (fn []
    (let [position (:position @app)
          zoom (:zoom @app)
          points (:points @app)
          geojson (:geojson @app)]
      [:div
       [:h2 "Leaflet Map"]
       [:p "Lat " (format-coord (first position)) " Lon " (format-coord (second position)) " Zoom " zoom]
       [leaflet/map-component {:position position
                               :zoom zoom
                               :points points
                               :geojson geojson
                               :on-position-changed (fn [new-pos]
                                                      (swap! app assoc-in [:position] new-pos))
                               :on-zoom-changed (fn [new-zoom]
                                                  (swap! app assoc-in [:zoom] new-zoom))}]
       [:div.actions
        [:button {:on-click #(swap! app (constantly initial-app-state))} "Reset view"]]])))

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
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  ;; (fetch-points)
  (fetch-geojson)
  (mount-root))
