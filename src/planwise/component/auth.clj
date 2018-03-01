(ns planwise.component.auth
  (:require [planwise.boundary.auth :as boundary]
            [planwise.boundary.users :as users]
            [planwise.model.ident :as ident]
            [planwise.auth.guisso :as guisso]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [ring.util.request :refer [request-url]]
            [ring.util.response :as resp]
            [clj-time.core :as time]
            [clojure.string :as str]
            [buddy.sign.jwt :as jwt]
            [oauthentic.core :as oauth]
            [slingshot.slingshot :refer [try+ throw+]]
            [planwise.util.ring :refer [absolute-url]]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Service implementation

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
                        (assoc :identity (ident/user->ident user)))]
    (info (str "User " user-email " (id=" (:id user) ") authenticated successfully"))
    (users/update-user-last-login! users-store (:id user))
    (assoc response :session session)))

(defn logout
  "Modifies the response to logout the currently authenticated user"
  [response service]
  (assoc response :session nil))

(defn after-logout-url
  "Returns the URL to redirect the user to after a successful logout"
  [service]
  (guisso-url service "/users/sign_out"))

(defn create-jwe-token
  "Create a JWE token for the client to authenticate for API calls"
  [service user-ident]
  (let [secret  (:jwe-secret service)
        options (:jwe-options service)
        claims  (assoc user-ident :exp (time/plus (time/now) (time/seconds 3600)))]
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

(defn- fetch-token-from-guisso
  "Performs an OAuth2 token request to Guisso using the service configuration
  and catching possible invalid grant errors and returning nil in such cases."
  [service params]
  (try+
   (let [url (guisso-url service "/oauth2/token")
         params (assoc params
                       :client-id (:guisso-client-id service)
                       :client-secret (:guisso-client-secret service))]
     (-> (oauth/fetch-token url params)
         (guisso-response->token-info)))
   (catch [:status 400] {body :body}
     ;; this should happen on invalid grants
     (warn (str "Received a 400 from the OAuth server: " body))
     nil)
   (catch Object _
     (error (:throwable &throw-context) "unexpected error")
     (throw+))))

(defn oauth2-fetch-token
  [service auth-code return-url]
  (info "Fetching OAuth2 token")
  (fetch-token-from-guisso service {:redirect-uri return-url :code auth-code}))

(defn oauth2-refresh-token
  [service refresh-token]
  (info "Refreshing OAuth2 token")
  (fetch-token-from-guisso  service {:refresh-token refresh-token}))

(defn token-expired?
  [{expires :expires :as token}]
  (time/before? expires (time/plus (time/now) (time/seconds 10))))

(defn save-auth-token!
  [{:keys [users-store] :as service} scope user-ident token]
  (let [email (ident/user-email user-ident)]
    (info "Saving OAuth2 token for user" email "on scope" scope)
    (users/save-token-for-scope! users-store scope email token)
    token))

(defn find-auth-token
  [{:keys [users-store] :as service} scope user-ident]
  (let [email (ident/user-email user-ident)
        _     (debug "Looking for token for" email "on scope" scope)
        token (users/find-latest-token-for-scope users-store scope email)]
    (if (and token (token-expired? token))
      (do (info (str "Token for " email " (" scope ") found but it expired. Refreshing."))
          (let [new-token (oauth2-refresh-token service (:refresh-token token))]
            (when new-token
              (save-auth-token! service scope user-ident new-token))))
      token)))


;; ----------------------------------------------------------------------
;; Service definition

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
  boundary/Auth
  (find-auth-token [service scope user-ident]
    (find-auth-token service scope user-ident))
  (create-jwe-token [service user-ident]
    (create-jwe-token service user-ident)))

(defmethod ig/init-key :planwise.component/auth
  [_ config]
  (assoc (map->AuthService config)
         :manager (guisso/openid-manager)))
