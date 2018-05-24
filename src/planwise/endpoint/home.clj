(ns planwise.endpoint.home
  (:require [compojure.core :refer :all]
            [planwise.boundary.maps :as maps]
            [planwise.boundary.auth :as auth]
            [planwise.util.ring :as util]
            [planwise.config :as config]
            [integrant.core :as ig]
            [cheshire.core :as json]
            [hiccup.form :refer [hidden-field]]
            [hiccup.page :refer [include-js include-css html5]]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [clojure.java.io :as io]))

(def inline-svg
  (slurp (io/resource "svg/icons.svg")))

(def mount-target
  [:div#app
   [:div#loading
    [:h3 "Loading Application"]
    [:p "Please wait..."]
    [:img {:src "/images/logo-transparent.png"}]]])

(def mount-target2
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

(defn head2 []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   [:title "PlanWise"]
   (include-css "/assets/leaflet/leaflet.css")
   (include-css "/css/site2.css")
   [:link {:href "https://fonts.googleapis.com/css?family=Roboto:300,400,500" :rel "stylesheet"}]
   [:link {:href "https://fonts.googleapis.com/icon?family=Material+Icons" :rel "stylesheet"}]])

(defn client-config
  [{:keys [auth resmap request maps globals]}]
  (let [resmap-url (:url resmap)
        mapserver-url (maps/mapserver-url maps)
        default-capacity (maps/default-capacity maps)
        calculate-demand (maps/calculate-demand? maps)
        ident (util/request-ident request)
        email (util/request-user-email request)
        token (auth/create-jwe-token auth ident)
        app-version config/app-version
        config {:resourcemap-url resmap-url
                :identity email
                :jwe-token token
                :mapserver-url mapserver-url
                :app-version app-version
                :facilities-default-capacity default-capacity
                :calculate-demand calculate-demand}]

    [:script (str "var _CONFIG=" (json/generate-string config) ";")]))

(defn loading-page
  [{:keys [auth] :as endpoint} request]
  (if-not (authenticated? request)
    (throw-unauthorized)
    (html5
     (head)
     [:body
      inline-svg
      mount-target
      (anti-forgery-field)
      (client-config (assoc endpoint :request request))
      (include-js "/assets/leaflet/leaflet.js")
      (include-js "/js/leaflet.grouprenderer.js")
      (include-js "/js/leaflet.bboxloader.js")
      (include-js "/js/leaflet.legend.js")
      (include-js "/js/main.js")
      [:script "planwise.client.core.main();"]])))

(defn loading-page2
  [{:keys [auth] :as endpoint} request]
  (if-not (authenticated? request)
    (throw-unauthorized)
    (html5
     (head2)
     [:body {:class "mdc-typography"}
      inline-svg
      mount-target2
      (anti-forgery-field)
      (client-config (assoc endpoint :request request))
      (include-js "/assets/leaflet/leaflet.js")
      (include-js "/js/leaflet.grouprenderer.js")
      (include-js "/js/leaflet.bboxloader.js")
      (include-js "/js/leaflet.legend.js")
      (include-js "/js/main.js")
      [:script "planwise.client.core.main();"]])))

(defn home-endpoint [endpoint]
  (let [loading-page (partial loading-page endpoint)
        loading-page2 (partial loading-page2 endpoint)]
    (routes
     (GET "/" [] loading-page2)
     (GET "/old" [] loading-page)
     (GET "/_design" [] loading-page2)
     (GET "/_design/:section" [] loading-page2)
     #_(GET "/crash" [] (throw (RuntimeException. "Crash")))
     (GET "/datasets" [] loading-page)
     (GET "/providers-set" [] loading-page2)
     (context "/projects2" []
       (GET "/" [] loading-page2)
       (GET "/:id" [] loading-page2)
       (GET "/:id/scenarios" [] loading-page2)
       (GET "/:id/settings" [] loading-page2)
       (GET "/:project-id/scenarios/:id" [] loading-page2))
     (context "/projects/:id" []
       (GET "/" [] loading-page)
       (GET "/facilities" [] loading-page)
       (GET "/transport" [] loading-page)
       (GET "/scenarios" [] loading-page)
       (GET "/access/:token" [] loading-page)))))

(defmethod ig/init-key :planwise.endpoint/home
  [_ config]
  (home-endpoint config))
