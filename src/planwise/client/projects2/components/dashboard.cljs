(ns planwise.client.projects2.components.dashboard
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
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
   [ui/secondary-action {:on-click #(reset! delete? true)} "Delete project"]])

(defn- scenarios-list-item
  [scenario]
  [:tr {:key (:id scenario) :on-click #(dispatch [:scenarios/load-scenario {:id (:id scenario)}])}
   [:td {:class "col1"} (:name scenario)]
   [:td {:class "col2"} (:demand-coverage scenario)]
   [:td {:class "col3"} (:investment scenario)]
   [:td {:class "col4"} ""]])

(defn- scenarios-list
  [scenarios current-project]
  [:div.scenarios-content
   [:table
    [:thead
     [:tr
      [:th {:class "col1"} "Name"]
      [:th {:class "col2"} (str "Demand coverage (" (get-in current-project [:config :demographics :unit-name]) ")")]
      [:th {:class "col3"} "Investment"]
      [:th {:class "col4"} "Actions"]]]

    (into [:tbody] (map scenarios-list-item scenarios))]])

(defn view-current-project
  []
  (let [current-project (rf/subscribe [:projects2/current-project])
        delete?  (r/atom false)
        scenarios-sub (rf/subscribe [:scenarios/list])
        scenarios-invalid (rf/subscribe [:scenarios/list-is-invalid])]
    (fn []
      (let [scenarios (asdf/value @scenarios-sub)]
        (when (or (asdf/should-reload? @scenarios-sub) @scenarios-invalid)
          (rf/dispatch [:scenarios/load-scenarios]))
        (cond
          (or @scenarios-invalid (nil? scenarios)) [common2/loading-placeholder]
          :else
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
            (scenarios-list scenarios @current-project)]])))))
