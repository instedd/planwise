(ns viewer.handler
  (:require [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [compojure.core :refer [GET POST routes wrap-routes defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [viewer.middleware :refer [wrap-middleware]]
            [ring.util.response :refer [response]]
            [viewer.routing :as routing]
            [clojure.data.json :as json]
            [clojure.stacktrace :as stacktrace]
            [config.core :refer [env]]))

(def mount-target
  [:div#app
   [:h3 "Loading Application"]
   [:p "Please wait..."]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css "//cdn.leafletjs.com/leaflet/v0.7.7/leaflet.css")
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(def loading-page
  (html5
    (head)
    [:body {:class "body-container"}
     mount-target
     (include-js "//cdn.leafletjs.com/leaflet/v0.7.7/leaflet.js")
     (include-js "/js/app.js")]))

(defn calculate-isochrone [req]
  (let [params (:params req)
        node-id (:node-id params)
        threshold (:threshold params)]
    (if (or (empty? node-id) (empty? threshold))
      {:status 400
       :headers {}
       :body "invalid query"}
      (try
        (let [polygon (routing/isochrone (Integer. node-id) (Float. threshold))]
          (response polygon))
        (catch Exception e
          (do
            (println (str "Exception processing query: " (.. e (getClass) (getName)) ": " (.getMessage e)))
            (stacktrace/print-stack-trace e)
            (println "=============================")
            {:status 400
             :headers {}
             :body "invalid query"}))))))

(defn nearest-node [{:keys [params]}]
  (let [lat (:lat params)
        lon (:lon params)]
    (if (or (empty? lat) (empty? lon))
      {:status 400
       :headers {}
       :body "invalid query"}
      (try
        (let [node (routing/nearest-node (Float. lat) (Float. lon))]
          (if node
            (response (json/write-str {:id (:id node)
                                       :point (:point node)}))
            (response nil)))
        (catch Exception e
          (do
            (println (str "Exception processing query: " (.. e (getClass) (getName)) ": " (.getMessage e)))
            (stacktrace/print-stack-trace e)
            (println "=============================")
            {:status 400
             :headers {}
             :body "invalid query"}))))))

(defroutes site-routes
  (GET "/" [] loading-page)

  (resources "/")
  (not-found "Not Found"))

(defroutes api-routes
  (POST "/nearest-node" [] nearest-node)
  (POST "/isochrone" [] calculate-isochrone))

(def all-routes
  (routes
   (wrap-defaults api-routes api-defaults)
   (wrap-defaults site-routes site-defaults)))

(def app (wrap-middleware #'all-routes))
