(ns planwise.client.api
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :as async :refer [chan put! <!]]
            [ajax.core :refer [GET to-interceptor default-interceptors]]
            [re-frame.utils :as c]
            [re-frame.core :refer [dispatch]]
            [clojure.string :as string]))

;; Set default interceptor for adding CSRF token to all non-GET requests

(def csrf-token
  (atom (.-value (.getElementById js/document "__anti-forgery-token"))))

(def csrf-token-interceptor
  (to-interceptor {:name "CSRF Token Interceptor"
                   :request (fn [req]
                              (if (not= "GET" (:method req))
                                (assoc-in req [:headers "X-CSRF-Token"] @csrf-token)
                                req))}))

(swap! default-interceptors (partial cons csrf-token-interceptor))


;; Common json-request definition to use with ajax requests
(defn common-success-fn [data]
  (c/log "API response: " data))

(defn common-error-fn [{:keys [status status-text]}]
  (c/error (str "Error " status " performing AJAX request: " status-text)))

(defn json-request [params [success-fn error-fn]]
  (let [success-handler (cond
                          (fn? success-fn) success-fn
                          (nil? success-fn) common-success-fn
                          :else #(dispatch [success-fn %]))
        error-handler (or error-fn common-error-fn)]
    {:format :json
     :response-format :json
     :keywords? true
     :params params
     :handler success-handler
     :error-handler error-handler}))

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

(defn parse-node-info [node-data]
  (when-not (empty? node-data)
    (let [node-id (node-data "id")
          geojson (.parse js/JSON (node-data "point"))
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
