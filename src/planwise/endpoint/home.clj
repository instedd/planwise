(ns planwise.endpoint.home
  (:require [compojure.core :refer :all]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [planwise.component.auth :refer [create-jwe-token]]
            [hiccup.form :refer [hidden-field]]
            [hiccup.page :refer [include-js include-css html5]]))

(def mount-target
  [:div#app
   [:h3 "Loading Application"]
   [:p "Please wait..."]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   [:title "PlanWise"]
   (include-css "/assets/normalize.css/normalize.css")
   (include-css "/assets/leaflet/leaflet.css")
   (include-css "/css/re-com.css")
   (include-css "/css/site.css")])

(defn identity-field [request]
  (let [email (-> request :identity :user)]
    (hidden-field "__identity" email)))

(defn jwe-token-field [auth request]
  (let [email (-> request :identity :user)
        token (create-jwe-token auth email)]
    (hidden-field "__jwe-token" token)))

(defn loading-page [auth request]
  (if-not (authenticated? request)
    (throw-unauthorized)
    (html5
     (head)
     [:body
      mount-target
      (anti-forgery-field)
      (identity-field request)
      (jwe-token-field auth request)
      (include-js "/assets/leaflet/leaflet.js")
      (include-js "/js/main.js")
      [:script "planwise.client.core.main();"]])))

(defn home-endpoint [{:keys [auth]}]
  (let [loading-page (partial loading-page auth)]
    (routes
     (GET "/" [] loading-page)
     (GET "/playground" [] loading-page)
     (context "/projects/:id" []
       (GET "/" [] loading-page)
       (GET "/facilities" [] loading-page)
       (GET "/transport" [] loading-page)
       (GET "/scenarios" [] loading-page)))))
