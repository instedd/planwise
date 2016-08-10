(ns planwise.endpoint.home
  (:require [compojure.core :refer :all]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [planwise.boundary.maps :as maps]
            [cheshire.core :as json]
            [hiccup.form :refer [hidden-field]]
            [hiccup.page :refer [include-js include-css html5]]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [planwise.util.ring :as util]
            [planwise.component.auth :refer [create-jwe-token]]))

(def mount-target
  [:div#app
   [:div#loading
    [:h3 "Loading Application"]
    [:p "Please wait..."]
    [:img {:src "/images/logo-transparent.png"}]]])

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

(defn client-config
  [{:keys [auth resmap request maps globals]}]
  (let [resmap-url (:url resmap)
        mapserver-url (maps/mapserver-url maps)
        ident (util/request-ident request)
        email (util/request-user-email request)
        token (create-jwe-token auth ident)
        app-version (or (:app-version globals) "unspecified")
        config {:resourcemap-url resmap-url
                :identity email
                :jwe-token token
                :mapserver-url mapserver-url
                :app-version app-version}]
    [:script (str "var _CONFIG=" (json/generate-string config) ";")]))

(defn loading-page
  [{:keys [auth] :as endpoint} request]
  (if-not (authenticated? request)
    (throw-unauthorized)
    (html5
     (head)
     [:body
      mount-target
      (anti-forgery-field)
      (client-config (assoc endpoint :request request))
      (include-js "/assets/leaflet/leaflet.js")
      (include-js "/js/leaflet.geojsongroups.js")
      (include-js "/js/main.js")
      [:script "planwise.client.core.main();"]])))

(defn home-endpoint [endpoint]
  (let [loading-page (partial loading-page endpoint)]
    (routes
     (GET "/" [] loading-page)
     (GET "/playground" [] loading-page)
     (GET "/datasets" [] loading-page)
     (context "/projects/:id" []
       (GET "/" [] loading-page)
       (GET "/facilities" [] loading-page)
       (GET "/transport" [] loading-page)
       (GET "/scenarios" [] loading-page)))))
