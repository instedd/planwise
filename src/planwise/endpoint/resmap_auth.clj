(ns planwise.endpoint.resmap-auth
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [redirect response content-type]]
            [hiccup.page :refer [html5]]
            [buddy.auth :refer [throw-unauthorized authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.util.ring :refer [absolute-url]]
            [planwise.component.auth :as auth]
            [planwise.component.resmap :as resmap]))

(def oauth2-callback-page
  (html5
   [:script "window.opener.postMessage('authenticated', window.location.origin);window.close();"]))

(defn resmap-auth-routes
  [{:keys [auth resmap]}]
  (routes
   (GET "/start" req
     (let [scope (resmap/auth-scope resmap)
           return-url (absolute-url "/oauth2/callback" req)
           authorisation-url (auth/oauth2-authorization-url auth scope return-url)]
       (redirect authorisation-url)))
   (GET "/callback" [code :as req]
     (let [scope (resmap/auth-scope resmap)
           return-url (absolute-url "/oauth2/callback" req)
           token (auth/oauth2-fetch-token auth code return-url)
           user (:identity req)]
       (auth/save-auth-token! auth scope user token)
       (-> (response oauth2-callback-page)
           (content-type "text/html"))))))

(defn resmap-auth-endpoint
  [system]
  (context "/oauth2" []
    (restrict (resmap-auth-routes system) {:handler authenticated?})))
