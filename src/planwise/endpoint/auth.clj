(ns planwise.endpoint.auth
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [ring.util.request :refer [request-url]]
            [ring.util.response :refer [content-type response redirect header]]
            [cheshire.core :as json]
            [hiccup.page :refer [html5]]
            [clojure.string :as string]
            [buddy.auth :refer [throw-unauthorized authenticated?]]
            [planwise.util.ring :as util]
            [planwise.component.auth :as auth]))

(def logout-page
  (html5
   [:body
    [:p "Logged out"]
    [:p [:a {:href "/login"} "Login"]]]))

(def failure-page
  (html5
   [:body
    [:p "Authentication failure"]
    [:p [:a {:href "/login"} "Try again"]]]))

(defn next-url [req]
  (let [next (-> req :params :next)]
    (if (and next (not (string/blank? next)))
      next
      "/")))

(defn auth-endpoint [{service :auth}]
  (routes
   (GET "/identity" req
     (if-not (authenticated? req)
       (throw-unauthorized)
       (let [ident (util/request-ident req)
             token (auth/create-jwe-token service ident)]
        (html5
         [:body
          [:p (str "Current identity: " (util/request-user-email req)
                   " id=" (util/request-user-id req))]
          [:p
           "API Token:"
           [:br]
           [:code {:style "white-space: normal; word-wrap: break-word;"}
            token]]
          [:p
           "Example usage:"
           [:br]
           [:code {:style "white-space: normal; word-wrap: break-word;"}
            "$ curl -H 'Authorization: Token " token "' "
            (util/absolute-url "/api/whoami" req)]]
          [:p
           [:a {:href "/logout"} "Logout"]]]))))

   (GET "/login" req
     (let [next-url (-> req :params :next)]
       (auth/openid-redirect service req (str "/openidcallback?next=" next-url))))

   (GET "/openidcallback" req
     (if-let [identity (auth/openid-validate service req)]
       (as-> (redirect (next-url req)) $
           (auth/login service $ req identity))
       (-> (response failure-page)
           (content-type "text/html"))))

   (GET "/logout" []
     (-> (response logout-page)
         (content-type "text/html")
         (auth/logout service)))

   (DELETE "/logout" []
     (let [redirect-after-logout (auth/after-logout-url service)]
       (-> (response (json/generate-string {:redirect-to redirect-after-logout}))
           (content-type "application/json")
           (auth/logout service))))))

(defmethod ig/init-key :planwise.endpoint/auth
  [_ config]
  (auth-endpoint config))
