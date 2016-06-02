(ns planwise.endpoint.home
  (:require [compojure.core :refer :all]
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
   (include-css "/assets/normalize.css/normalize.css")
   (include-css "/assets/leaflet/leaflet.css")
   (include-css "/css/site.css")])

(def loading-page
  (html5
    (head)
    [:body {:class "body-container"}
     mount-target
     (include-js "/assets/leaflet/leaflet.js")
     (include-js "/js/main.js")]))

(defn home-endpoint [system]
  (GET "/" [] loading-page))
