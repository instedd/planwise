(ns planwise.component.auth
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [ring.util.request :refer [request-url]]
            [ring.util.response :as resp]
            [planwise.util.ring :refer [absolute-url]]
            [planwise.auth.guisso :as guisso]))

(timbre/refer-timbre)

(defrecord AuthService [manager openid-identifier realm]
  component/Lifecycle
  (start [component]
    (if-not (:manager component)
      (assoc component :manager (guisso/openid-manager))
      component))

  (stop [component]
    (assoc component :manager nil)))

(defn auth-service
  [config]
  (map->AuthService config))


(defn redirect
  "Setup the OpenID associations and return a Ring response to redirect the user
  to the OpenID login page"
  [service request return-location]
  (let [manager         (:manager service)
        realm           (or (:realm service) (absolute-url "/" request))
        identifier      (:openid-identifier service)
        session         (:session request)
        return-url      (absolute-url return-location request)
        auth-request    (guisso/auth-request manager identifier return-url realm)
        destination-url (:destination-url auth-request)
        auth-state      (:auth-state auth-request)]
    (assoc (resp/redirect destination-url)
           :session (merge session {:openid-state auth-state}))))

(defn validate
  "Validates that the request contains a positive OpenID assertion and the
  authenticated user information"
  [service request]
  (let [manager    (:manager service)
        session    (:session request)
        auth-state (:openid-state session)
        url        (request-url request)
        params     (:params request)]
    (if auth-state
      (if-let [authentication (guisso/auth-validate manager auth-state url params)]
        authentication
        (do
          (info "Authentication failure")
          nil))
      (do
        (warn "No OpenID request in session found")
        nil))))

(defn login
  "Updates the response to alter the session to include the authenticated user"
  [response request identity]
  (let [user-email (:email identity)
        session    (-> (:session request)
                       (assoc :identity user-email))]
    (assoc response :session session)))

(defn logout
  "Modifies the response to logout the currently authenticated user"
  [response]
  (assoc response :session nil))
