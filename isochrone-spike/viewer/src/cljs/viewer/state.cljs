(ns viewer.state
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET]]
            [clojure.string :as string]))


;; The one and only application state
(defonce app (atom {}))

;; Used for resetting the viewport
(def initial-position-and-zoom {:position [-1.29 36.83]
                                :zoom 9})

(defn debounced
  ([f timeout]
   (let [id (atom nil)]
     (fn [& args]
       (js/clearTimeout @id)
       (reset! id (js/setTimeout
                   (apply partial (cons f args))
                   timeout))))))

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
  (GET "/isochrone" {:format :raw
                     :params {:node-id node-id
                              :threshold (/ threshold 50.0)}
                     :handler on-geojson-data}))

(def fetch-isochrone
  (debounced fetch-isochrone* 500))

(defn on-nearest-node [data]
  (when-not (empty? data)
    (let [node-data (.parse js/JSON data)
          node-id (.-id node-data)
          geojson (.parse js/JSON (.-point node-data))
          point (reverse (js->clj (.-coordinates geojson)))
          threshold (or (:threshold @app) 10)]
      (swap! app merge {:node-id node-id
                        :points [point]})
      (fetch-isochrone node-id threshold))))

(defn fetch-nearest-node [lat lon]
  (let [zoom (:zoom @app)]
    (GET "/nearest-node" {:format :raw
                          :params {:lat lat
                                   :lon lon}
                          :handler on-nearest-node})))

;; Actions
;; -------------------------------

(defn init! []
  (println "Initializing state")
  (reset! app (merge {:threshold 30}
                     initial-position-and-zoom)))

(defn reset-viewport []
  (swap! app merge initial-position-and-zoom))


(defn update-threshold [new-threshold]
  (let [node-id (:node-id @app)
        current-threshold (:threshold @app)]
    (when (and node-id (not= current-threshold new-threshold))
      (fetch-isochrone node-id new-threshold))
    (swap! app assoc-in [:threshold] new-threshold)))

(defn update-position [new-position]
  (swap! app assoc-in [:position] new-position))

(defn update-zoom [new-zoom]
  (swap! app assoc-in [:zoom] new-zoom))
