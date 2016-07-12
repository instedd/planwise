(ns planwise.endpoint.auth
  (:require [compojure.core :refer :all]
            [ring.util.request :refer [request-url]]
            [ring.util.response :refer [content-type response redirect header]]
            [hiccup.page :refer [html5]]
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

(defn auth-endpoint [{service :auth}]
  (routes
   (GET "/identity" req
     (let [id (:identity (:session req))]
       (html5
        [:body
         [:p (str "Current identity: " id)]
         [:p [:a {:href "/logout"} "Logout"]]])))

   (GET "/login" req
     (auth/redirect service req "/openidcallback?"))

   (GET "/openidcallback" req
     (if-let [identity (auth/validate service req)]
       (-> (redirect "/identity")
           (auth/login req identity))
       (-> (response failure-page)
           (content-type "text/html"))))

   (GET "/logout" []
     (-> (response logout-page)
         (content-type "text/html")
         (auth/logout)))))
