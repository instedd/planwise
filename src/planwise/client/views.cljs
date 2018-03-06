(ns planwise.client.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.config :as config]
            [planwise.client.routes :as routes]
            [planwise.client.components.nav :as nav]
            [planwise.client.components.common :refer [icon]]
            [planwise.client.projects.views :as projects]
            [planwise.client.current-project.views :as current-project]
            [planwise.client.datasets.views :as datasets]))


(def nav-items
  [{:item :home :href (routes/home) :title "Projects"}
   {:item :datasets :href (routes/datasets) :title "Datasets"}])

(def current-user-email
  (atom config/user-email))

(defn signout-button
  []
  [:button.signout
   {:type :button
    :on-click #(dispatch [:signout])}
   [icon :signout "icon-small"]])

(defn nav-bar []
  (let [current-page (subscribe [:current-page])]
    (fn []
      (let [active @current-page]
        [:header
         [:a.logo {:href (routes/home)}
          (icon :logo)]
         [:nav [nav/ul-menu nav-items active]]
         [:div.user-info
          @current-user-email
          [signout-button]]]))))

(defmulti content-pane identity)

(defmethod content-pane :home []
  [projects/project-list-page])

(defmethod content-pane :projects []
  [current-project/project-page])

(defmethod content-pane :datasets []
  [datasets/datasets-page])

(defn planwise-app []
  (let [current-page (subscribe [:current-page])]
    (fn []
      [:div
       [nav-bar]
       [content-pane @current-page]
       [:footer
        [:span.version (str "Version: " config/app-version)]]])))
