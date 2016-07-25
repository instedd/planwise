(ns planwise.client.api
  (:require [ajax.core :refer [GET to-interceptor default-interceptors]]
            [re-frame.utils :as c]
            [re-frame.core :refer [dispatch]]
            [clojure.string :as string]
            [planwise.client.config :as config]))

;; Set default interceptor for adding CSRF token to all non-GET requests

(def csrf-token
  (atom (.-value (.getElementById js/document "__anti-forgery-token"))))

(def csrf-token-interceptor
  (to-interceptor {:name "CSRF Token Interceptor"
                   :request (fn [req]
                              (if (not= "GET" (:method req))
                                (assoc-in req [:headers "X-CSRF-Token"] @csrf-token)
                                req))}))

;; Add interceptor to send JWT encrypted token authentication with all requests

(def jwe-token
  (atom config/jwe-token))

(def jwe-token-interceptor
  (to-interceptor {:name "JWE Token Interceptor"
                   :request (fn [req]
                              (let [auth-value (str "Token " @jwe-token)]
                                (assoc-in req [:headers "Authorization"] auth-value)))}))

(swap! default-interceptors into [csrf-token-interceptor jwe-token-interceptor])


;; Common request definitions to use with ajax requests

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


;; Debugging utility functions

(defn truncate
  ([s]
   (truncate s 200))
  ([s max-length]
   (let [s (str s)]
     (if (> (count s) max-length)
       (str (subs s 0 (- max-length 3)) "...")
       s))))
