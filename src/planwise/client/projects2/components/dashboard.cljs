(ns planwise.client.projects2.components.dashboard
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [clojure.string :refer [blank? capitalize]]
            [planwise.client.asdf :as asdf]
            [planwise.client.projects2.components.common :refer [delete-project-dialog]]
            [planwise.client.components.common2 :as common2]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.utils :as utils]
            [planwise.client.projects2.components.settings :as settings]))

(defn- project-tabs
  [{:keys [active] :or {active :scenarios}}]
  [m/TabBar {:activeTabIndex ({:scenarios 0 :settings 1} active)
             :on-change (fn [evt]
                          (let [tab-index (.-value (.-target evt))]
                            (case tab-index
                              0 (dispatch [:projects2/project-scenarios])
                              1 (dispatch [:projects2/project-settings]))))}
   [m/Tab "Scenarios"]
   [m/Tab "Settings"]])

(defn- project-secondary-actions
  [project delete?]
  [[ui/secondary-action {:on-click #(dispatch [:projects2/reset-project (:id project)])} "Back to draft"]
   [ui/secondary-action {:on-click #(reset! delete? true)} "Delete project"]])

(defn- create-chip
  [input]
  (when (not (blank? input)) [m/ChipSet [m/Chip [m/ChipText input]]]))

(defn- scenarios-list-item
  [project-id {:keys [id name label state demand-coverage investment changeset-summary] :as scenario}]
  [:tr {:key id :on-click (fn [evt]
                            (if (or (.-shiftKey evt) (.-metaKey evt))
                              (.open js/window (routes/scenarios {:project-id project-id :id id}))
                              (dispatch [:scenarios/load-scenario {:id id}])))}
   [:td (cond (= state "pending") [create-chip state]
              (not= label "initial") [create-chip label])]
   [:td.col  name]
   [:td.col  (utils/format-number demand-coverage)]
   [:td.col  (utils/format-number investment)]
   [:td.actions changeset-summary]])

(defn- scenarios-list
  [scenarios current-project ]
  [:div.scenarios-content
   [:table
    [:caption (utils/or-blank (:name current-project) [:p ""])]
    [:thead
     [:tr
      [:th {:key "col1"}]
      [:th {:key "col2" } "Name"]
      [:th.col {:key "col3"}(str (capitalize (get-in current-project [:config :demographics :unit-name])) " coverage")]
      [:th.col {:key "col4"}"Investment"]
      [:th.actions {:key "col5"}"Actions"]]]
    [:tbody
     (map #(scenarios-list-item (:id current-project) %) scenarios)]]])

(defn- project-settings
  []
  [settings/current-project-settings-view {:read-only true}])

(defn view-current-project
  [active-tab]
  (let [current-project (rf/subscribe [:projects2/current-project])
        delete?  (r/atom false)
        hide-dialog (fn [] (reset! delete? false))
        id (:id @current-project)
        scenarios-sub (rf/subscribe [:scenarios/list])]
    (fn [active-tab]
      (let [scenarios (asdf/value @scenarios-sub)]
        (when (asdf/should-reload? @scenarios-sub)
          (rf/dispatch [:scenarios/load-scenarios]))
        (cond
          (asdf/reloading? scenarios) [common2/loading-placeholder]
          :else
          [ui/fixed-width (merge (common2/nav-params)
                                 {:title (:name @current-project)
                                  :tabs [project-tabs {:active active-tab}]
                                  :secondary-actions (project-secondary-actions @current-project delete?)})
           [delete-project-dialog {:open? @delete?
                                   :cancel-fn hide-dialog
                                   :delete-fn #(rf/dispatch [:projects2/delete-project id])}]
           [ui/panel {}
            (case active-tab
              :scenarios [scenarios-list scenarios @current-project]
              :settings  [project-settings])]])))))
