(ns planwise.client.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.playground.views :as playground]
            [planwise.client.projects.views :as projects]))


(def nav-items
  [{:page #{:home} :href "/" :title "Projects"}
   {:page #{:playground} :href "/playground" :title "Playground"}])

(defn nav-item [{:keys [active page href title]}]
  [:li {:class (when (page active) "active")}
   [:a {:href href} title]])

(defn nav-bar []
  (let [current-page (subscribe [:current-page])]
    (fn []
      (let [active @current-page]
        [:header
         [:a.logo {:href "/"}
          [:h1 "PlanWise"]]
         [:nav [:ul
                (map-indexed (fn [idx item]
                               [nav-item (assoc item :key idx :active active)])
                             nav-items)]]]))))

(defmulti content-pane identity)

(defmethod content-pane :home []
  [projects/list-view])

(defmethod content-pane :playground []
  [playground/playground-page])

(defn planwise-app []
  (let [current-page (subscribe [:current-page])]
    (fn []
      [:div
       [nav-bar]
       [content-pane @current-page]])))
