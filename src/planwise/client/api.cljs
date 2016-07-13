(ns planwise.client.api
  (:require [ajax.core :refer [GET to-interceptor default-interceptors]]
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
(defn common-error-fn [{:keys [status status-text]}]
  (c/error (str "Error " status " performing AJAX request: " status-text)))

(defn success-handler [success-fn]
  (cond
    (fn? success-fn) success-fn
    (nil? success-fn) #(c/log "API response: " %)
    :else #(dispatch [success-fn %])))

(defn raw-request [params [success-fn error-fn] & {:keys [mapper-fn], :or {mapper-fn identity}}]
  (let [error-handler (or error-fn common-error-fn)]
    {:format :raw
     :params params
     :handler (comp (success-handler success-fn) mapper-fn)
     :error-handler error-handler}))

(defn json-request [params fns & keyargs]
  (assoc (raw-request params fns keyargs)
    :format :json
    :response-format :json
    :keywords? true))

(defn fetch-geojson [& fns]
  (GET "/data/poly.geojson" (raw-request {} fns :mapper-fn #(.parse js/JSON %))))


;; The threshold parameter for isochrones should be given in seconds
;; Here the traffic-constant is an approximate modifier to the threshold using
;; Google Maps directions for comparison. The topology graph was built using the
;; mapconfig.xml in this repo.

(def traffic-constant 0.66)

(defn fetch-isochrone [node-id threshold algorithm & handler-fns]
    (GET "/routing/isochrone" (raw-request {:node-id node-id
                                             :threshold (* threshold traffic-constant)
                                             :algorithm (some-> algorithm name)}
                                          handler-fns
                                          :mapper-fn #(.parse js/JSON %))))

(defn parse-node-info [node-data]
  (when-not (empty? node-data)
    (let [node-id (node-data "id")
          geojson (.parse js/JSON (node-data "point"))
          point (reverse (js->clj (aget geojson "coordinates")))]
      {:node-id node-id
       :point point})))

(defn fetch-nearest-node [lat lon & fns]
  (let [params {:lat lat :lon lon}]
    (GET "/routing/nearest-node" (raw-request params fns :mapper-fn parse-node-info))))

(defn fetch-facilities [& fns]
  (GET "/facilities" (raw-request {} fns)))

(defn fetch-facilities-with-isochrones [threshold algorithm simplify & fns]
  (let [params {:threshold threshold
                :algorithm algorithm
                :simplify simplify}]
    (GET "/facilities/with-isochrones" (raw-request params fns))))


;; Debugging utility functions

(defn truncate
  ([s]
   (truncate s 200))
  ([s max-length]
   (let [s (str s)]
     (if (> (count s) max-length)
       (str (subs s 0 (- max-length 3)) "...")
       s))))
