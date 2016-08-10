(ns planwise.endpoint.auth
  (:require [compojure.core :refer :all]
            [ring.util.request :refer [request-url]]
            [ring.util.response :refer [content-type response redirect header]]
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
       (let [ident (util/request-ident req)]
        (html5
         [:body
          [:p (str "Current identity: " (util/request-user-email req)
                   " id=" (util/request-user-id req))]
          [:p
           "API Token:"
           [:br]
           [:code {:style "white-space: normal; word-wrap: break-word;"}
            (auth/create-jwe-token service ident)]]
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
         (auth/logout service)))))
