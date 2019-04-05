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

(def mount-target2
  [:div#app
   [:h3 "Loading Application"]
   [:p "Please wait..."]])

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
  [{:keys [auth request maps intercom-app-id]}]
  (let [mapserver-url (maps/mapserver-url maps)
        ident (util/request-ident request)
        email (util/request-user-email request)
        token (auth/create-jwe-token auth ident)
        app-version config/app-version
        config {:identity email
                :jwe-token token
                :mapserver-url mapserver-url
                :app-version app-version
                :intercom-app-id intercom-app-id}]

    [:script (str "var _CONFIG=" (json/generate-string config) ";")]))

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
      (include-js "/js/leaflet.markercluster.js")
      (include-css "/css/MarkerCluster.css")
      (include-css "/css/MarkerCluster.Default.css")
      (include-css "/css/MarkerCluster.Custom.css")
      [:script "planwise.client.core.main();"]])))

(defn landing-page
  []
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name    "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:title "PlanWise"]
    (include-css "/css/landing.css")
    [:link {:href "https://fonts.googleapis.com/css?family=Roboto:300,400,500" :rel "stylesheet"}]
    [:link {:href "https://fonts.googleapis.com/icon?family=Material+Icons" :rel "stylesheet"}]]
   [:body
    [:header
     [:div.navMenu.icon.hideDesktop {:onclick "openNav()"} "menu"]
     [:div
      [:a.logo {:href "#"}
       [:img.right.responsive-img {:src   "/images/planwise-icon.svg"
                                   :width "60px"}]
       [:span "Planwise"]]]
     [:nav#menu.hideTablet
      [:ul
       [:li
        [:a.btn
         {:href   "http://instedd.org/technologies/"
          :target "_blank"}
         "About Instedd Platform"]]
       [:li
        [:a.btn
         {:href "/login"}
         "Sign in"]]
       [:li
        [:a.btn-outline
         {:href   "https://docs.google.com/forms/d/e/1FAIpQLSc8ldIy9lF8jw12kIZky94MIAEq1o1jCkCiL4TqmLAx3jlZng/viewform?usp=sf_link"
          :target "_blank"}
         "Work with Us"]]]]]
    [:main
     [:div.section.headline
      [:div.grid.x2
       [:div.cell
        [:h1.strong "Placing Help Where It Is Needed"]
        [:h3 "A data-driven tool that helps health planners to make better decisions"]
        [:p.text-gray
         [:b "What is Planwise?"]
         [:br]
         "PlanWise is a new tool that helps planners and responders in low-resource settings see how
          they can serve as many people as they can, as cost-effectively as they can."]]
       [:div.cell
        [:img.right.responsive-img.mwidth100 {:src "/images/illustration.svg"}]]]]
     [:div.section
      [:div.grid.center-text
       [:div.cell
        [:h1
         [:span "Identify "]
         "most impactful interventions"]]
       [:div.cell
        [:img.responsive-img {:src "/images/project-thumbnail1@2x.png"}]]]]
     [:hr]
     [:div.section.centerTablet.no-padd-right.v-height
      [:div.grid.x2
       [:div.cell
        [:h1
         [:span "Visualize "]
         "coverage by travel time"]
        [:i.icon.large-icon.text-gray-light.outline "directions_walk"]]
       [:div.cell.right-text
        [:img.responsive-img.mwidth100 {:src "/images/project-thumbnail2@2x.png"}]]]]
     [:hr]
     [:div.section
      [:div.grid.center-text
       [:div.cell
        [:h1
         [:span "Utilize "]
         "existing data sets"]
        [:div.center-text.margin
         [:i.icon.large-icon.text-gray-light.outline "storage"]]]
       [:div.cell
        [:img.center-align.responsive-img {:src "/images/project-thumbnail3@2x.png"}]]]]
     [:div.section.center-text.illustrated.v-height
      [:h1 "Identify where help is needed most"]
      [:p.margin
       "To deliver transparent, data-driven analysis for decision makers, PlanWise applies algorithms
        and geospatial optimization techniques to existing data on population, road networks and resources,
        so planners can better understand the unmet needs of their constituents. PlanWise's use of special
        algorithms to predict population based impact of allocations eliminates potential bias and provides
        data and evidence for humanitarian and development professionals."]]]
    [:footer
     [:div.section.medium.centerTablet
      [:ul
       [:li
        [:a
         {:href   "http://instedd.org"
          :target "_blank"}
         "InSTEDD"]]
       [:li
        [:a
         {:href   "http://instedd.org/terms-of-service/"
          :target "_blank"}
         "Terms of service"]]
       [:li
        [:a
         {:href "mailto:support@instedd.org?subject=[planwise]"}
         "Support"]]]]]
    [:a#Overlay.overlay {:href "javascript:void(0)" :onclick "closeNav()"}]
    [:div#Sidenav.sidenav
     [:ul
      [:li
       [:a.btn.block
        {:href   "http://instedd.org/technologies/"
         :target "_blank"}
        "About Instedd Platform"]]
      [:li
       [:a.btn.block
        {:href "login"}
        "Sign in"]]
      [:li
       [:a.btn-outline.block
        {:href   "https://docs.google.com/forms/d/e/1FAIpQLSc8ldIy9lF8jw12kIZky94MIAEq1o1jCkCiL4TqmLAx3jlZng/viewform?usp=sf_link"
         :target "_blank"}
        "Work with Us"]]]]

    [:script "
       function openNav() {
           document.getElementById(\"Sidenav\").style.transform = \"translateX(0)\";
           document.getElementById(\"Overlay\").style.visibility = \"visible\";
       }

       function closeNav() {
           document.getElementById(\"Sidenav\").style.transform = \"translateX(-100%)\";
           document.getElementById(\"Overlay\").style.visibility = \"hidden\";
       }
     "]]))

(defn home-page
  [endpoint request]
  (if-not (authenticated? request)
    (landing-page)
    (loading-page2 endpoint request)))

(defn home-endpoint [endpoint]
  (let [loading-page2 (partial loading-page2 endpoint)]
    (routes
     (GET "/" [] (partial home-page endpoint))
     #_(GET "/_design" [] loading-page2)
     #_(GET "/_design/:section" [] loading-page2)
     #_(GET "/crash" [] (throw (RuntimeException. "Crash")))
     (GET "/providers" [] loading-page2)
     (GET "/sources" [] loading-page2)
     (context "/projects2" []
       (GET "/" [] loading-page2)
       (GET "/:id" [] loading-page2)
       (GET "/:id/scenarios" [] loading-page2)
       (GET "/:id/settings" [] loading-page2)
       (GET "/:project-id/scenarios/:id" [] loading-page2)))))

(defmethod ig/init-key :planwise.endpoint/home
  [_ config]
  (home-endpoint config))
