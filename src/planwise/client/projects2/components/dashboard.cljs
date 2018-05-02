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

(defn- create-chip
  [input]
  [m/ChipSet [m/Chip [m/ChipText input]]])

(defn- scenarios-list-item
  [project-id {:keys [id name label state demand-coverage investment changeset-summary] :as scenario}]
  [:tr {:key id :on-click (fn [evt]
                            (cond
                              (or (.-shiftKey evt) (.-metaKey evt)) (.open js/window (routes/scenarios {:project-id project-id :id id}))
                              :else (dispatch [:scenarios/load-scenario {:id id}])))}
   [:td {:class "col1"} (cond (= state "pending") [create-chip state]
                              (not= label "initial") [create-chip label])]
   [:td {:class "col2"} name]
   [:td {:class "col3"} demand-coverage]
   [:td {:class "col4"} investment]
   [:td {:class "col5"} changeset-summary]])

(defn- scenarios-list
  [scenarios current-project]
  [:div.scenarios-content
   [:table
    [:thead
     [:tr
      [:th {:class "col1"} ""]
      [:th {:class "col2"} "Name"]
      [:th {:class "col3"} (str (get-in current-project [:config :demographics :unit-name]) " coverage")]
      [:th {:class "col4"} "Investment"]
      [:th {:class "col5"} "Actions"]]]

    (into [:tbody] (map #(scenarios-list-item (:id current-project) %) scenarios))]])

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
