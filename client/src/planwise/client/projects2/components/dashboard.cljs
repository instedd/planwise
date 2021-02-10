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
  [project-id {:keys [id name label state demand-coverage investment changeset-count changeset-summary geo-coverage population-under-coverage] :as scenario} index analysis-type]
  (if id
    [:tr {:key id :on-click (fn [evt]
                              (if (or (.-shiftKey evt) (.-metaKey evt))
                                (.open js/window (routes/scenarios {:project-id project-id :id id}))
                                (dispatch [:scenarios/load-scenario {:id id}])))}
     [:td (cond (= state "pending") [create-chip state]
                (not= label "initial") [create-chip label])]
     [:td.col1 name]
     [:td.col2 (utils/format-number demand-coverage)]
     [:td.col5 (str (utils/format-number (* geo-coverage 100)) "%")]
     [:td.col6 population-under-coverage]
     [:td.col3 (if (= analysis-type "budget") (utils/format-number investment) changeset-count)]
     [:td.col4 changeset-summary]]
    [:tr {:key (str "tr-" index)}
     (map (fn [n] [:td {:key (str "td-" index "-" n)}]) (range 7))]))

(defn- generate-title
  [num]
  (str (utils/pluralize num "scenario")))

(defn- scenarios-list
  [scenarios current-project]
  (let [num (count scenarios)
        analysis-type (get-in current-project [:config :analysis-type])]
    [:div.scenarios-content
     [:table
      [:caption (generate-title num)]
      [:thead
       [:tr
        [:th]
        [:th.col1 "Name"]
        [:th.col2 (str (some-> (get-in current-project [:config :demographics :unit-name]) capitalize) " coverage")]
        [:th.col5 "Geographic Coverage"]
        [:th.col6 "Population Under Coverage"]
        [:th.col3 (if (= analysis-type "budget") "Investment" "Effort")]
        [:th.col4 "Actions"]]]
      [:tbody
       (map-indexed (fn [index scenario] (scenarios-list-item (:id current-project) scenario index analysis-type)) (into scenarios (repeat (- 5 num) nil)))]]]))

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
