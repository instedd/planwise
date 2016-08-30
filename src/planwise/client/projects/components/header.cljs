(ns planwise.client.projects.components.header
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.utils :as utils]
            [planwise.client.components.nav :as nav]
            [planwise.client.routes :as routes]))

(defn project-tab-items [project-id wizard-state]
  (let [route-params {:id project-id}
        wizard-mode-on (:set wizard-state)
        wizard-tabs-state (:tabs wizard-state)
        tab-items [{:item :demographics
                    :href (routes/project-demographics route-params)
                    :title "Demographics"}
                   {:item :facilities
                    :href (routes/project-facilities route-params)
                    :title "Facilities"}
                   {:item :transport
                    :href (routes/project-transport route-params)
                    :title "Transport Means"}
                   #_{:item :scenarios
                      :href (routes/project-scenarios route-params)
                      :title "Scenarios"}]]
    (if wizard-mode-on
      (->> tab-items
           (mapv #(assoc %
                         :wizard-state ((:item %) wizard-tabs-state)
                         :tab-number (+ 1 (.indexOf [:demographics :facilities :transport] (:item %)))))
           (mapv #(dissoc % :icon)))
      tab-items)))

(defn header-section [project-id project-goal selected-tab wizard-mode-state]
  [:div.project-header
   [:h2 project-goal]
   [:a.sub-link {:href (routes/home)} "Back"]
   [:nav
    [nav/ul-menu (project-tab-items project-id wizard-mode-state) selected-tab]
    [:div
     [:a
      {:href "#" :on-click (utils/with-confirm #(dispatch [:projects/delete-project project-id]) "Are you sure you want to delete this project?")}
      "Delete project"]]]])
