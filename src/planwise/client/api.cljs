(ns planwise.client.api
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :as async :refer [chan put! <!]]
            [ajax.core :refer [GET]]
            [clojure.string :as string]))


;; Default handlers to use with cljs-ajax to put the response back into a
;; core.async channel

(defn async-handlers
  ([c]
   (async-handlers c identity))
  ([c success-mapper-fn]
   {:handler (fn [response]
               (put! c {:status :ok
                        :data (success-mapper-fn response)}))
    :error-handler (fn [{:keys [status status-text]}]
                     (put! c {:status :error
                              :code status
                              :message status-text}))}))

(defn parse-points-csv [csv]
  (let [lines (string/split-lines csv)]
    (->> lines
         (filter #(not= \# (first %)))
         (map #(mapv js/parseFloat (string/split % #","))))))

(defn fetch-points []
  (let [c (chan)]
    (GET "/data/data.csv" (async-handlers c parse-points-csv))
    c))

(defn parse-geojson [s]
  (.parse js/JSON s))

(defn fetch-geojson []
  (let [c (chan)]
    (GET "/data/poly.geojson" (async-handlers c parse-geojson))
    c))


;; The threshold parameter for isochrones should be given in seconds
;; Here the traffic-constant is an approximate modifier to the threshold using
;; Google Maps directions for comparison. The topology graph was built using the
;; mapconfig.xml in this repo.

(def traffic-constant 0.66)

(defn fetch-isochrone [node-id threshold & [algorithm]]
  (let [c (chan)]
    (GET "/routing/isochrone" (assoc (async-handlers c parse-geojson)
                                     :format :raw
                                     :params {:node-id node-id
                                              :threshold (* threshold traffic-constant)
                                              :algorithm (some-> algorithm name)}))
    c))

(defn parse-node-info [data]
  (when-not (empty? data)
    (let [node-data (.parse js/JSON data)
          node-id (aget node-data "id")
          geojson (.parse js/JSON (aget node-data "point"))
          point (reverse (js->clj (aget geojson "coordinates")))]
      {:node-id node-id
       :point point})))

(defn fetch-nearest-node [lat lon]
  (let [c (chan)]
    (GET "/routing/nearest-node" (assoc (async-handlers c parse-node-info)
                                        :format :raw
                                        :params {:lat lat
                                                 :lon lon}))
    c))

(defn fetch-facilities []
  (let [c (chan)]
    (GET "/facilities" (assoc (async-handlers c identity)
                              :format :json))
    c))

(defn fetch-facilities-with-isochrones [threshold algorithm simplify]
  (let [c (chan)]
    (GET "/facilities/with-isochrones" (assoc (async-handlers c identity)
                                              :format :json
                                              :params {:threshold threshold
                                                       :algorithm algorithm
                                                       :simplify simplify}))
    c))

;; Debugging utility functions

(defn truncate
  ([s]
   (truncate s 200))
  ([s max-length]
   (let [s (str s)]
     (if (> (count s) max-length)
       (str (subs s 0 (- max-length 3)) "...")
       s))))

(defn async-get-and-print [c]
  (go
    (let [msg (<! c)]
      (condp = (:status msg)
        :ok (println (str "Data received OK: " (truncate (:data msg))))
        :error (println (str "Error receiving data (" (:code msg) "): " (:message msg)))))))
