(ns planwise.client.projects2.components.dashboard
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [planwise.client.projects2.components.settings :as settings]
            [planwise.client.asdf :as asdf]
            [planwise.client.dialog :refer [new-dialog]]
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
  [project delete?]
  [[ui/secondary-action {:on-click #(dispatch [:projects2/reset-project (:id project)])} "Back to draft"]
   [ui/secondary-action {:on-click #(reset! delete? true)} "Delete Project"]])

(defn view-current-project
  []
  (let [current-project (subscribe [:projects2/current-project])
        delete?  (r/atom false)]
    (fn []
      [ui/fixed-width (merge (common2/nav-params)
                             {:title (:name @current-project)
                              :tabs [project-tabs {:active 0}]
                              :secondary-actions (project-secondary-actions @current-project delete?)})
       [new-dialog {:open? @delete?
                    :title "Delete Project"
                    :accept-fn #(dispatch [:projects2/delete-project (:id @current-project)])
                    :cancel-fn #(reset! delete? false)
                    :content [:p "Do you want to delete this project?"]}]
       [ui/panel {}
        [:div {} "TBD"]]])))
