(ns planwise.client.state
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET]]
            [clojure.string :as string]
            [cljs.core.async :as async :refer [chan >! <! put!]]
            [planwise.client.api :as api])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


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
       (condp = (first args)
         :cancel nil
         :immediate (apply f (drop 1 args))
         (reset! id (js/setTimeout
                     (apply partial (cons f args))
                     timeout)))))))

(defn async-handle [c success-fn]
  (go
    (let [result (<! c)]
      (condp = (:status result)
        :ok (success-fn (:data result))
        :error (.error js/console (str "Error " (:code result) " performing AJAX request: " (:message result)))))))


(defn fetch-points []
  (async-handle (api/fetch-points)
                #(swap! app assoc-in [:points] %)))

(defn fetch-geojson []
  (async-handle (api/fetch-geojson)
                #(swap! app assoc-in [:geojson] %)))

(defn fetch-isochrone* [node-id threshold]
  (async-handle (api/fetch-isochrone node-id threshold)
                #(swap! app assoc-in [:isochrone] %)))

(def fetch-isochrone (debounced fetch-isochrone* 500))

(defn fetch-nearest-node [lat lon]
  (async-handle (api/fetch-nearest-node lat lon)
                (fn [{:keys [node-id point]}]
                  (let [threshold (or (:threshold @app) 10)]
                    (swap! app merge {:node-id node-id
                                      :points [point]})
                    (fetch-isochrone :immediate node-id threshold)))))

(defn fetch-facilities []
  (async-handle (api/fetch-facilities)
                #(swap! app assoc-in [:facilities] %)))

;; Actions
;; -------------------------------

(defn init! []
  (let [c (chan)]
    (println "Initializing state")
    (fetch-facilities)
    (reset! app (assoc initial-position-and-zoom
                       :threshold 600
                       :channel c))
    (go-loop []
      (let [msg (<! c)]
        (println (str "Application loop received " msg))
        (recur)))))

(defn send [msg]
  (let [c (:channel @app)]
    (put! c msg)))

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
