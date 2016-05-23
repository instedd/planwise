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

(def initial-position-and-zoom {:position [-1.29 36.83]
                                :zoom 9})
(def initial-state (merge {:threshold 40000} initial-position-and-zoom))

(defonce app (atom initial-state))

(defn debounced
  ([f timeout]
   (let [id (atom nil)]
     (fn [& args]
       (js/clearTimeout @id)
       (reset! id (js/setTimeout
                   (apply partial (cons f args))
                   timeout))))))

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

(defn fetch-isochrone* [node-id threshold]
  (POST "/isochrone" {:format :raw
                      :params {:node-id node-id
                               :threshold threshold}
                      :handler on-geojson-data}))

(def fetch-isochrone
  (debounced fetch-isochrone* 500))

(defn on-nearest-node [data]
  (when-not (empty? data)
    (let [node-data (.parse js/JSON data)
          node-id (.-id node-data)
          geojson (.parse js/JSON (.-point node-data))
          point (reverse (js->clj (.-coordinates geojson)))
          threshold (or (:threshold @app) 10000)]
      (swap! app merge {:node-id node-id
                        :points [point]})
      (fetch-isochrone node-id threshold))))

(defn fetch-nearest-node [lat lon]
  (let [zoom (:zoom @app)]
    (POST "/nearest-node" {:format :raw
                           :params {:lat lat
                                    :lon lon}
                           :handler on-nearest-node})))

;; -------------------------
;; Views

(defn home-page []
  (fn []
    (let [position (:position @app)
          zoom (:zoom @app)
          points (:points @app)
          geojson (:geojson @app)
          threshold (:threshold @app)]
      [:div
       [:div.threshold-control
        [:input {:type "range"
                 :min 5000 :max 400000
                 :value threshold
                 :on-change (fn [e]
                              (let [node-id (:node-id @app)
                                    new-threshold (js/parseInt (-> e .-target .-value))]
                                (when node-id
                                  (fetch-isochrone node-id new-threshold))
                                (swap! app assoc-in [:threshold] new-threshold)))}
         [:span.small (str threshold)]]]
       [leaflet/map-component {:position position
                               :zoom zoom
                               :points points
                               :geojson geojson
                               :on-click (fn [lat lon]
                                           (fetch-nearest-node lat lon))
                               :on-position-changed (fn [new-pos]
                                                      (swap! app assoc-in [:position] new-pos))
                               :on-zoom-changed (fn [new-zoom]
                                                  (swap! app assoc-in [:zoom] new-zoom))}]
       [:div.actions
        [:button {:on-click #(swap! app merge initial-position-and-zoom)} "Reset view"]
        [:div.pull-right
         [:span.small "Lat " (format-coord (first position)) " Lon " (format-coord (second position)) " Zoom " zoom]]]])))

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
  ;; (fetch-geojson)
  (mount-root))
