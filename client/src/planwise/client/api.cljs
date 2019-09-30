(ns planwise.client.api
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]
            [clojure.string :as string]
            [planwise.client.config :as config]))

;; Set default interceptor for adding CSRF token to all non-GET requests

(def csrf-token
  (atom (.-value (.getElementById js/document "__anti-forgery-token"))))

(def csrf-token-interceptor
  (ajax/to-interceptor {:name "CSRF Token Interceptor"
                        :request (fn [req]
                                   (if (not= "GET" (:method req))
                                     (assoc-in req [:headers "X-CSRF-Token"] @csrf-token)
                                     req))}))

;; Add interceptor to send JWT encrypted token authentication with all requests

(def jwe-token
  (atom config/jwe-token))

(def jwe-token-interceptor
  (ajax/to-interceptor {:name "JWE Token Interceptor"
                        :request (fn [req]
                                   (let [auth-value (str "Token " @jwe-token)]
                                     (assoc-in req [:headers "Authorization"] auth-value)))}))

(swap! ajax/default-interceptors into [csrf-token-interceptor jwe-token-interceptor])


;; Default event handlers for http-xhrio/api effects

(rf/reg-event-fx
 :http-no-on-success
 (fn [_ [_ data]]
   (rf/console :log "Unhandled successful API response: " data)))

(rf/reg-event-fx
 :http-no-on-failure
 (fn [_ [_ {:keys [status] :as response}]]
   (rf/console :error (str "Got HTTP response " status ":") response)))


;; Debugging utility functions

(defn truncate
  ([s]
   (truncate s 200))
  ([s max-length]
   (let [s (str s)]
     (if (> (count s) max-length)
       (str (subs s 0 (- max-length 3)) "...")
       s))))


;; Authentication APIs

(def signout
  {:method :delete
   :uri    "/logout"})
