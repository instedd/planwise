(ns planwise.client.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.config :as config]
            [planwise.client.routes :as routes]
            [planwise.client.components.nav :as nav]
            [planwise.client.components.common :refer [icon]]
            [planwise.client.playground.views :as playground]
            [planwise.client.projects.views :as projects]
            [planwise.client.datasets.views :as datasets]))


(def nav-items
  [{:item #{:home :projects} :href (routes/home) :title "Projects"}
   {:item :datasets :href (routes/datasets) :title "Datasets"}
   #_{:item :playground :href (routes/playground) :title "Playground"}])

(def current-user-email
  (atom config/user-email))

(defn nav-bar []
  (let [current-page (subscribe [:current-page])]
    (fn []
      (let [active @current-page]
        [:header
         [:a.logo {:href (routes/home)}
          (icon :logo)]
         [:nav [nav/ul-menu nav-items active]]
         [:div.user-info
          @current-user-email]]))))

(defmulti content-pane identity)

(defmethod content-pane :home []
  [projects/project-list-page])

(defmethod content-pane :projects []
  [projects/project-page])

(defmethod content-pane :playground []
  [playground/playground-page])

(defmethod content-pane :datasets []
  [datasets/datasets-page])

(defn planwise-app []
  (let [current-page (subscribe [:current-page])]
    (fn []
      [:div
       [nav-bar]
       [content-pane @current-page]
       [:footer
        [:span.version (str "Version: "config/app-version)]]])))
