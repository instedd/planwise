(ns planwise.auth.guisso
  (:require [planwise.model.ident :as ident]
            [taoensso.timbre :as timbre])
  (:import [org.openid4java.consumer ConsumerManager]
           [org.openid4java.discovery DiscoveryInformation]
           [org.openid4java.message.sreg SRegMessage SRegRequest SRegResponse]
           [org.openid4java.message.ax AxMessage FetchRequest FetchResponse]
           [org.openid4java.message ParameterList]
           [java.net URL]))

(timbre/refer-timbre)

(defn openid-manager
  []
  (ConsumerManager.))

(defn disco-info->auth-state
  [disco-info]
  (let [op-endpoint (.getOPEndpoint disco-info)]
    (.toExternalForm op-endpoint)))

(defn auth-state->disco-info
  [s]
  (let [url (URL. s)]
    (DiscoveryInformation. url)))

(defn auth-request
  "Creates an OpenID authentication request and returns the destination URL to
  redirect the user to along with the discovery information used (and
  associated)"
  ([manager identifier return-url]
   (auth-request manager identifier return-url return-url))
  ([manager identifier return-url realm]
   (let [discoveries (.discover manager identifier)
         disco-info  (.associate manager discoveries)
         request     (.authenticate manager disco-info return-url realm)
         sreg-req    (doto (SRegRequest/createFetchRequest)
                       (.addAttribute "fullname" true)
                       (.addAttribute "nickname" true)
                       (.addAttribute "email" true))]
     (.addExtension request sreg-req)
     {:auth-state (disco-info->auth-state disco-info)
      :destination-url (.getDestinationUrl request true)})))

(defn- map->hashmap
  [map]
  (reduce (fn [m [k v]]
            (.put m (name k) (str v)) m)
          (java.util.HashMap.) map))

(defn auth-validate
  [manager auth-state request-url params]
  (let [disco-info   (auth-state->disco-info auth-state)
        param-list   (ParameterList. (map->hashmap params))
        verification (.verify manager request-url param-list disco-info)
        response     (.getAuthResponse verification)
        verified?    (some? (.getVerifiedId verification))]
    (if (and verified? (.hasExtension response SRegMessage/OPENID_NS_SREG))
      (let [sreg-res (.getExtension response SRegMessage/OPENID_NS_SREG)
            email    (.getAttributeValue sreg-res "email")
            fullname (.getAttributeValue sreg-res "fullname")
            nickname (.getAttributeValue sreg-res "nickname")]
        {:email     email
         :fullname  fullname
         :nickname  nickname})
      (do
        (info "OpenID authentication failed or no SReg message found in the response")
        nil))))

(defn wrap-check-guisso-cookie
  "Ring middleware to check that the Guisso cookie value matches the currently
  authenticated user, usually from the session. This middleware needs to be
  placed after the authentication middleware but before the authorization. It's
  essentially authentication post-processing."
  [handler]
  (fn [request]
    (let [guisso-cookie (get-in request [:cookies "guisso"])
          ident (:identity request)
          user-email (some-> ident ident/user-email)]
      (if (and (some? guisso-cookie)
               (some? user-email)
               (not= user-email (:value guisso-cookie)))
        (do
          (info "Found Guisso cookie but is not equal to current session user")
          ;; Remove the identity from the request
          (handler (dissoc request :identity)))
        (handler request)))))
