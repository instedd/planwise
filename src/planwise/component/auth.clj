(ns planwise.component.auth
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [ring.util.request :refer [request-url]]
            [ring.util.response :as resp]
            [clj-time.core :as time]
            [clojure.string :as str]
            [buddy.sign.jwt :as jwt]
            [oauthentic.core :as oauth]
            [planwise.util.ring :refer [absolute-url]]
            [planwise.component.users :as users]
            [planwise.auth.guisso :as guisso]))

(timbre/refer-timbre)

(defrecord AuthService [;; Guisso configuration
                        guisso-url
                        guisso-client-id
                        guisso-client-secret

                        ;; OpenID realm
                        realm

                        ;; Secret and options for generating JWT tokens
                        jwe-secret
                        jwe-options

                        ;; Runtime state: OpenID consumer manager
                        manager

                        ;; Component dependencies
                        users-store]
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

(defn guisso-url
  ([service]
   (guisso-url service nil))
  ([service path]
   (let [path (if (str/starts-with? path "/") path (str "/" path))
         base-url (:guisso-url service)
         base-url (if (str/ends-with? base-url "/")
                    (.substring base-url 0 (dec (.length base-url)))
                    base-url)]
     (str base-url path))))

(defn- openid-identifier
  "Constructs the OpenID identifier endpoint from the base Guisso URL"
  [service]
  (guisso-url service "/openid"))

(defn openid-redirect
  "Setup the OpenID associations and return a Ring response to redirect the user
  to the OpenID login page"
  [service request return-location]
  (let [manager         (:manager service)
        realm           (or (:realm service) (absolute-url "/" request))
        identifier      (openid-identifier service)
        session         (:session request)
        return-url      (absolute-url return-location request)
        auth-request    (guisso/auth-request manager identifier return-url realm)
        destination-url (:destination-url auth-request)
        auth-state      (:auth-state auth-request)]
    (assoc (resp/redirect destination-url)
           :session (merge session {:openid-state auth-state}))))

(defn openid-validate
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
  [service response request identity]
  (let [user-email  (:email identity)
        users-store (:users-store service)
        user        (users/find-or-create-user-by-email users-store user-email)
        session     (-> (:session request)
                        (assoc :identity {:user user-email}))]
    (info (str "User " user-email " (id=" (:id user) ") authenticated successfully"))
    (users/update-user-last-login! users-store (:id user))
    (assoc response :session session)))

(defn logout
  "Modifies the response to logout the currently authenticated user"
  [service response]
  (assoc response :session nil))

(defn create-jwe-token
  "Create a JWE token for the client to authenticate for API calls"
  [service user-email]
  (let [secret  (:jwe-secret service)
        options (:jwe-options service)
        claims  {:user user-email
                 :exp (time/plus (time/now) (time/seconds 3600))}]
    (jwt/encrypt claims secret options)))

(defn oauth2-authorization-url
  [service scope return-url]
  (let [url (guisso-url service "/oauth2/authorize")
        options {:client-id (:guisso-client-id service)
                 :redirect-uri return-url
                 :scope scope}]
    (oauth/build-authorization-url url options)))

(defn- guisso-response->token-info
  [guisso-resp]
  {:expires (time/plus (time/now) (time/seconds (:expires_in guisso-resp)))
   :token (:access-token guisso-resp)
   :refresh-token (:refresh-token guisso-resp)})

(defn oauth2-fetch-token
  [service auth-code return-url]
  (-> (oauth/fetch-token (guisso-url service "/oauth2/token")
                         {:client-id (:guisso-client-id service)
                          :client-secret (:guisso-client-secret service)
                          :redirect-uri return-url
                          :code auth-code})
      (guisso-response->token-info)))

(defn oauth2-refresh-token
  [service refresh-token]
  (-> (oauth/fetch-token (guisso-url service "/oauth2/token")
                         {:client-id (:guisso-client-id service)
                          :client-secret (:guisso-client-secret service)
                          :refresh-token refresh-token})
      (guisso-response->token-info)))
