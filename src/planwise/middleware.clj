(ns planwise.middleware
  (:require [integrant.core :as ig]
            [compojure.response :as compojure]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [duct.core :as duct]
            [duct.middleware.web :refer [wrap-not-found wrap-hide-errors wrap-route-aliases]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [planwise.util.ring :refer [wrap-log-request wrap-log-errors]]
            [planwise.auth.guisso :refer [wrap-check-guisso-cookie]]))

(def ^:private error-404 (io/resource "planwise/errors/404.html"))
(def ^:private error-500 (io/resource "planwise/errors/500.html"))

(defn- html-response
  [html]
  (fn [request]
    (-> (compojure/render html request)
        (response/content-type "text/html; charset=UTF-8"))))

(defn- json-response
  [json]
  (fn [request]
    (-> (response/response json)
        (response/content-type "application/json"))))

(def session-config
  {:cookies true
   :session {:cookie-attrs {:max-age (* 24 3600)}
             :cookie-name "planwise-session"}})

;; TODO: hide and log errors in production environment
;; Old code (was in main.clj):
;; (def prod-config
;;     {:api {:middleware     [[wrap-log-errors]]}
;;      :app {:middleware     [[wrap-log-errors]
;;                             [wrap-hide-errors :internal-error]]
;;            :internal-error (io/resource "planwise/errors/500.html")}})

(defmethod ig/init-key :planwise.middleware/common
  [_ config]
  (fn [handler]
    (-> handler
        wrap-gzip
        (wrap-route-aliases {}))))

(defn- wrap-authentication-helper
  [handler backends]
  (apply wrap-authentication handler backends))

(defmethod ig/init-key :planwise.middleware/api
  [_ {:keys [environment authz-backend authn-backends session-store]}]
  (let [app-defaults (duct/merge-configs api-defaults
                                         session-config
                                         {:session {:store session-store}
                                          :params  {:nested true}})]
    (let [middleware [#(wrap-authorization % authz-backend)
                      #(wrap-authentication-helper % authn-backends)
                      #(wrap-log-request % {:exclude-uris #"^/(js|css|images|assets)/.*"})
                      wrap-keyword-params
                      wrap-json-params
                      wrap-json-response
                      #(wrap-defaults % app-defaults)]
          env-mw     (case environment
                       :production [wrap-log-errors
                                    #(wrap-hide-errors % (json-response {:error "internal error"}))]
                       [])]
      (apply comp (reverse (concat middleware env-mw))))))

(defmethod ig/init-key :planwise.middleware/app
  [_ {:keys [environment authz-backend authn-backends session-store]}]
  (let [app-defaults (duct/merge-configs site-defaults
                                         session-config
                                         {:session {:flash true
                                                    :store session-store}
                                          :static  {:resources "planwise/public"}})]
    (let [middleware [#(wrap-not-found % (html-response error-404))
                      wrap-webjars
                      #(wrap-resource % "public/assets")
                      #(wrap-authorization % authz-backend)
                      wrap-check-guisso-cookie
                      #(wrap-authentication-helper % authn-backends)
                      #(wrap-defaults % app-defaults)]
          env-mw     (case environment
                       :production [wrap-log-errors
                                    #(wrap-hide-errors % (html-response error-500))]
                       [])]
      (apply comp (reverse (concat middleware env-mw))))))
