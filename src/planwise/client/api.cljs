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

(defn default-error-handler [{:keys [status status-text]}]
  (c/error (str "Error " status " performing AJAX request: " status-text)))

(defn default-success-handler [data]
  (c/log "API response: " data))

(defn wrap-handler [callback default]
  (cond
    (fn? callback) callback
    (nil? callback) default
    (keyword? callback) #(dispatch [callback %])
    :else (do
            (c/error "Invalid handler " callback)
            default)))

(defn raw-request [params [success-fn error-fn] & {:keys [mapper-fn], :or {mapper-fn identity}}]
  (let [success-handler (wrap-handler success-fn default-success-handler)
        error-handler (wrap-handler error-fn default-error-handler)]
    {:format :raw
     :params params
     :handler (comp success-handler mapper-fn)
     :error-handler error-handler}))

(defn json-request [params fns & keyargs]
  (assoc (apply raw-request params fns keyargs)
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
