(ns planwise.client.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.routes :as routes]
            [planwise.client.common :as common]
            [planwise.client.playground.views :as playground]
            [planwise.client.projects.views :as projects]))


(def nav-items
  [{:item #{:home :projects} :href (routes/home) :title "Projects"}
   {:item :playground :href (routes/playground) :title "Playground"}])

(defn nav-bar []
  (let [current-page (subscribe [:current-page])]
    (fn []
      (let [active @current-page]
        [:header
         [:a.logo {:href (routes/home)}
          [:h1 "PlanWise"]]
         [:nav [common/ul-menu nav-items active]]]))))

(defmulti content-pane identity)

(defmethod content-pane :home []
  [projects/list-view])

(defmethod content-pane :projects []
  [projects/project-view])

(defmethod content-pane :playground []
  [playground/playground-page])

(defn planwise-app []
  (let [current-page (subscribe [:current-page])]
    (fn []
      [:div
       [nav-bar]
       [content-pane @current-page]])))
