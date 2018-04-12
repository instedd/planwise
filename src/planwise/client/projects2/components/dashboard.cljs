(ns planwise.client.projects2.components.dashboard
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common2 :as common2]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.utils :as utils]))

(defn- project-tabs
  [{:keys [active] :or {active 0}}]
  [m/TabBar {:activeTabIndex active}
   [m/Tab "Scenarios"]
   [m/Tab "Settings"]])

(defn- project-secondary-actions
  [project]
  [[ui/secondary-action {:on-click #(dispatch [:projects2/reset-project (:id project)])} "Back to draft"]])

(defn view-current-project
  []
  (let [current-project (subscribe [:projects2/current-project])]
    (fn []
      [ui/fixed-width (merge (common2/nav-params)
                             {:title (:name @current-project)
                              :tabs [project-tabs {:active 0}]
                              :secondary-actions (project-secondary-actions @current-project)})
       [ui/panel {}
        [:div {} "TBD"]]])))
