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
            [planwise.client.projects2.components.settings :as settings]
            [planwise.common :as common]))

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
  [{:keys [project-id id name label state demand-coverage effort changeset-summary geo-coverage population-under-coverage index source-demand analysis-type]}]
  (if id
    [:tr {:key id :on-click (fn [evt]
                              (if (or (.-shiftKey evt) (.-metaKey evt))
                                (.open js/window (routes/scenarios {:project-id project-id :id id}))
                                (dispatch [:scenarios/load-scenario {:id id}])))}
     [:td.col-state (cond (= state "pending") [create-chip state]
                          (not= label "initial") [create-chip label])]
     [:td.col-name name]
     [:td.col-demand-coverage (utils/format-number demand-coverage)]
     [:td.col-pop-without-service (utils/format-number (- population-under-coverage demand-coverage))]
     [:td.col-pop-without-coverage (utils/format-number (- source-demand population-under-coverage))]
     [:td.col-effort (utils/format-effort effort analysis-type)]
     (if (empty? changeset-summary)
       [:td.col-actions]
       [:td.col-actions.has-tooltip
        [:p changeset-summary]
        [:div.tooltip changeset-summary]])]
    [:tr {:key (str "tr-" index)}
     (map (fn [n] [:td {:key (str "td-" index "-" n)}]) (range 7))]))

(defn- generate-title
  [num source-demand]
  (str (common/pluralize num "scenario") " (Target population: " (utils/format-number source-demand) ")"))

(defn- sort-scenarios
  [scenarios key order]
  (letfn [(keyfn [scenario]
            (case key
              :without-service (- (:population-under-coverage scenario) (:demand-coverage scenario))
              ; Column 'Without coverage' displays `demand-source - population-under-coverage`
              :without-coverage (- (:population-under-coverage scenario))
              (get scenario key)))]
    (cond
      (or (nil? key) (nil? order)) scenarios
      :else (sort-by keyfn (if (= order :asc) #(< %1 %2) #(> %1 %2)) scenarios))))

(defn- next-order
  [order]
  (cond
    (nil? order) :asc
    (= order :asc) :desc
    :else nil))

(defn- scenarios-sortable-header
  [{:keys [sorting-key] :as props} title]
  (let [column (rf/subscribe [:scenarios/sort-column])
        order (rf/subscribe [:scenarios/sort-order])
        new-props (-> props
                      (dissoc :sorting-key)
                      (assoc
                       :on-click #(rf/dispatch [:scenarios/change-sort-column-order sorting-key (if (= sorting-key @column) (next-order @order) :asc)])
                       :order @order
                       :sorted (and (= sorting-key @column) (not (nil? @order)))))]
    [ui/sortable-table-header new-props title]))

(defn- scenarios-list
  [scenarios current-project]
  (let [num (count scenarios)
        source-demand (get-in current-project [:engine-config :source-demand])
        analysis-type (get-in current-project [:config :analysis-type])
        sort-column (rf/subscribe [:scenarios/sort-column])
        sort-order (rf/subscribe [:scenarios/sort-order])
        sorted-scenarios (sort-scenarios scenarios @sort-column @sort-order)]
    [:div.scenarios-content
     [:table.mdc-data-table__table
      [:caption (generate-title num source-demand)]
      [:thead.rmwc-data-table__head
       [:tr.rmwc-data-table__row.mdc-data-table__header-row
        [:th.col-state ""]
        [scenarios-sortable-header {:class [:col-name]
                                    :align :left
                                    :sorting-key :name}
         "Name"]
        [scenarios-sortable-header {:class [:col-demand-coverage]
                                    :align :right
                                    :sorting-key :demand-coverage
                                    :tooltip "Population within provider’s catchment area, with access to service provided within capacity."}
         "Population with service"]
        [scenarios-sortable-header {:class [:col-pop-without-service]
                                    :align :right
                                    :sorting-key :without-service
                                    :tooltip "Population within provider’s catchment area but without enough capacity to provide service"}
         "Without service"]
        [scenarios-sortable-header {:class [:col-pop-without-coverage]
                                    :align :right
                                    :sorting-key :without-coverage
                                    :tooltip "Population outside catchment area of service providers"}
         "Without coverage"]
        [scenarios-sortable-header {:class [:col-effort]
                                    :align :right
                                    :sorting-key :effort}
         (if (common/is-budget analysis-type) "Investment" "Effort")]
        [:th.col-actions [:p "Actions"]]]]
      [:tbody
       (map-indexed (fn [index scenario]
                      (scenarios-list-item (assoc scenario
                                                  :project-id (:id current-project)
                                                  :index index
                                                  :source-demand source-demand
                                                  :analysis-type analysis-type)))
                    (concat sorted-scenarios (repeat (- 5 num) nil)))]]]))

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
