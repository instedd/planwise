(ns planwise.auth
  (:require [integrant.core :as ig]
            [duct.core :as duct]
            [clojure.set :refer [rename-keys]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends :as backends]
            [buddy.core.nonce :as nonce]
            [clojure.java.io :as io]
            [compojure.response :as compojure]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.util.request :refer [request-url]]
            [ring.util.response :as response]))

(def ^:private error-403 (io/resource "planwise/errors/403.html"))

(defn api-unauthorized-handler
  [request metadata]
  (let [authenticated? (authenticated? request)
        error-response {:error "Unauthorized"}
        status (if authenticated? 403 401)]
    (-> (response/response error-response)
        (response/content-type "application/json")
        (response/status status))))

(defn app-unauthorized-handler
  [request metadata]
  (cond
    (authenticated? request)
    (-> (compojure/render error-403 request)
        (response/content-type "text/html; charset=UTF-8")
        (response/status 403))
    :else
    (let [current-url (request-url request)]
      (response/redirect (format "/login?next=%s" current-url)))))

(defmethod ig/init-key :planwise.auth/jwe-options   [_ options] options)
(defmethod ig/init-key :planwise.auth/jwe-secret    [_ secret] secret)
(defmethod ig/init-key :planwise.auth/cookie-secret [_ secret] secret)

(defmethod ig/init-key :planwise.auth.backend/jwe
  [_ config]
  (let [options (-> config
                    (rename-keys {:jwe-options :options
                                  :jwe-secret  :secret})
                    (duct/merge-configs {:unauthorized-handler api-unauthorized-handler}))]
    (backends/jwe options)))

(defmethod ig/init-key :planwise.auth.backend/session
  [_ config]
  (let [options (duct/merge-configs config
                                    {:unauthorized-handler app-unauthorized-handler})]
    (backends/session options)))

(defmethod ig/init-key :planwise.auth/session-store
  [_ options]
  (cookie-store options))
