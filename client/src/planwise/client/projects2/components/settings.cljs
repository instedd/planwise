(ns planwise.client.projects2.components.settings
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [clojure.string :refer [blank? join]]
            [planwise.client.asdf :as asdf]
            [planwise.client.dialog :refer [dialog]]
            [planwise.client.components.common2 :as common2]
            [planwise.client.projects2.components.common :refer [delete-project-dialog]]
            [planwise.client.coverage :refer [coverage-algorithm-filter-options]]
            [planwise.client.providers-set.components.dropdown :refer [providers-set-dropdown-component]]
            [planwise.client.sources.components.dropdown :refer [sources-dropdown-component]]
            [planwise.client.mapping :refer [static-image fullmap-region-geo]]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.filter-select :as filter-select]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.utils :as utils]
            [clojure.spec.alpha :as s]
            [leaflet.core :as l]
            [planwise.client.mapping :as mapping]))

;;------------------------------------------------------------------------
;;Current Project updating

(defn- regions-dropdown-component
  [attrs]
  (let [props (merge {:choices   @(rf/subscribe [:regions/list])
                      :label-fn  :name
                      :render-fn (fn [region] [:div.option-row
                                               [:span (:name region)]
                                               [:span.option-context (:country-name region)]])}
                     attrs)]
    (into [filter-select/single-dropdown] (mapcat identity props))))

;; TODO: Refactor this fn to require flat map instead of this large amount of params
(defn- current-project-input
  ([label path type]
   (current-project-input label path type "" "" {:disabled false}))
  ([label path type other-props]
   (current-project-input label path type "" "" {:disabled false}))
  ([label path type prefix suffix other-props]
   (let [current-project (rf/subscribe [:projects2/current-project])
         value           (or (get-in @current-project path) "")
         change-fn       #(rf/dispatch-sync [:projects2/save-key path %])
         props (merge (select-keys other-props [:class :disabled :sub-type])
                      {:prefix    prefix
                       :suffix    suffix
                       :label     label
                       :on-change (comp change-fn (fn [e] (-> e .-target .-value)))
                       :value     value})]
     (case type
       "number" [common2/numeric-field (assoc props :on-change change-fn)]
       [common2/text-field props]))))

(defn- project-start-button
  [_ project]
  [m/Button {:id         "start-project"
             :type       "button"
             :unelevated "unelevated"
             :disabled   (not (s/valid? :planwise.model.project/starting project))
             :on-click   (utils/prevent-default #(dispatch [:projects2/start-project (:id project)]))}
   (if (= (keyword (:state project)) :started) "Started ..." "Start")])


(defn- project-next-step-button
  [project next-step]
  [m/Button {:id         "start-project"
             :type       "button"
             :unelevated "unelevated"
             :disabled   (if (nil? next-step) (not (s/valid? :planwise.model.project/starting project)) false)
             :on-click   (utils/prevent-default #(if (nil? next-step)
                                                   (dispatch [:projects2/start-project (:id project)])
                                                   (dispatch [:projects2/navigate-to-step-project (:id project) next-step])))}
   "Continue"])

(defn- project-delete-button
  [state]
  [m/Button {:type     "button"
             :theme    ["text-secondary-on-secondary-light"]
             :on-click #(reset! state true)} "Delete"])

(defn- project-back-button
  [project step]
  (when step
    [m/Button {:type     "button"
               :theme    ["text-secondary-on-secondary-light"]
               :on-click (utils/prevent-default #(dispatch [:projects2/navigate-to-step-project (:id project) step]))}
     "Back"]))

(defn- tag-chip
  [props index input read-only]
  [m/Chip props [m/ChipText input]
   (when-not read-only [m/ChipIcon {:use "close"
                                    :on-click #(dispatch [:projects2/delete-tag index])}])])

(defn- tag-set
  [tags read-only]
  [m/ChipSet {:class "tags"}
   (for [[index tag] (map-indexed vector tags)]
     [tag-chip {:key (str "tag-" index)} index tag read-only])])

(defn tag-input []
  (let [value (r/atom "")]
    (fn []
      [common2/text-field {:type "text"
                           :placeholder "Type tag for filtering providers"
                           :on-key-press (fn [e] (when (and (= (.-charCode e) 13) (not (blank? @value)))
                                                   (dispatch [:projects2/save-tag @value])
                                                   (reset! value "")))
                           :on-change #(reset! value (-> % .-target .-value))
                           :value @value}])))

(defn- count-providers
  [tags {:keys [provider-set-id providers region-id]}]
  (let [{:keys [total filtered]} providers]
    (cond (nil? region-id) [:p "Select region first."]
          (nil? provider-set-id) [:p "Select provider set first."]
          :else [:p "Selected providers: " filtered " / " total])))

(defn- section-header
  [number title]
  [:div {:class-name "step-header"}
   [:h2 [:span title]]])

(defn- project-setting-title
  [icon title]
  [:div.project-setting-title [:p [m/Icon icon] title]])

;-------------------------------------------------------------------------------------------
; Actions
(defn- show-action
  [_ {:keys [idx action-name capacity investment] :as action} props]
  [:div {:class "project-setting"}
   [m/Button (merge
              {:type "button"
               :theme    ["text-secondary-on-secondary-light"]
               :on-click #(dispatch [:projects2/delete-action action-name idx])}
              props)
    [m/Icon "clear"]]
   (when (= action-name :build) "with a capacity of ")
   [current-project-input "" [:config :actions action-name idx :capacity] "number" "" "" (merge {:class "action-input"} props)]
   " would cost "
   [current-project-input "" [:config :actions action-name idx :investment] "number" "$" "" (merge {:class "action-input"} props)]])

(defn- listing-actions
  [{:keys [read-only? action-name list]}]
  [:div
   (for [[index action] (map-indexed vector list)]
     [show-action {:key (str action-name "-" index)} (assoc action :action-name action-name :idx index) {:disabled read-only?}])
   [m/Button  {:type "button"
               :disabled read-only?
               :theme    ["text-secondary-on-secondary-light"]
               :on-click #(dispatch [:projects2/create-action action-name])} [m/Icon "add"] "Add Option"]])

;-------------------------------------------------------------------------------------------

(defn- current-project-step-goal
  [read-only]
  (let [current-project (subscribe [:projects2/current-project])]
    [:section.project-settings-section
     [section-header 1 "Goal"]
     [current-project-input "Goal" [:name] "text"]
     [m/TextFieldHelperText {:persistent true} "Enter the goal for this project"]

     [regions-dropdown-component {:label     "Region"
                                  :on-change #(dispatch [:projects2/save-key :region-id %])
                                  :model     (:region-id @current-project)
                                  :disabled? read-only}]]))
(defn- current-project-step-consumers
  [read-only]
  (let [current-project (subscribe [:projects2/current-project])]
    [:section {:class-name "project-settings-section"}
     [section-header 2 "Consumers"]
     [sources-dropdown-component {:label     "Consumer Dataset"
                                  :value     (:source-set-id @current-project)
                                  :on-change #(dispatch [:projects2/save-key :source-set-id %])
                                  :disabled?  read-only}]
     [current-project-input "Consumers Unit" [:config :demographics :unit-name] "text" {:disabled read-only}]
     [m/TextFieldHelperText {:persistent true} (str "How do you refer to the filtered population? (Eg: women)")]
     [:div.percentage-input
      [current-project-input "Target" [:config :demographics :target] "number" "" "%"  {:disabled read-only :sub-type :percentage}]
      [:p (str "of " (or (not-empty (get-in @current-project [:config :demographics :unit-name])) "population") " should be considered")]]]))

(defn- current-project-step-providers
  [read-only]
  (let [current-project (subscribe [:projects2/current-project])
        tags            (subscribe [:projects2/tags])]
    [:section {:class-name "project-settings-section"}
     [section-header 3 "Providers"]
     [providers-set-dropdown-component {:label     "Provider Dataset"
                                        :value     (:provider-set-id @current-project)
                                        :on-change #(dispatch [:projects2/save-key :provider-set-id %])
                                        :disabled? read-only}]

     [current-project-input "Capacity Workload" [:config :providers :capacity] "number" {:disabled read-only :sub-type :float}]
     [m/TextFieldHelperText {:persistent true} (str "How many " (or (not-empty (get-in @current-project [:config :demographics :unit-name])) "consumers") " can each provider handle?")]
     (when-not read-only [tag-input])
     [:label "Tags: " [tag-set @tags read-only]]
     [count-providers @tags @current-project]]))

(defn- current-project-step-coverage
  [read-only]
  (let [current-project (subscribe [:projects2/current-project])]
    [:section {:class-name "project-settings-section"}
     [section-header 4 "Coverage"]
     [:div {:class "step-info"} "These values will be used to estimate the geographic coverage that your current sites are providing. That in turn will allow Planwise to calculate areas out of reach."]
     [coverage-algorithm-filter-options {:coverage-algorithm (:coverage-algorithm @current-project)
                                         :value              (get-in @current-project [:config :coverage :filter-options])
                                         :on-change          #(dispatch [:projects2/save-key [:config :coverage :filter-options] %])
                                         :empty              [:div {:class-name " no-provider-set-selected"} "First choose provider-set."]
                                         :disabled?          read-only}]]))

(defn- current-project-step-actions
  [read-only build-actions upgrade-actions]
  (let [current-project (subscribe [:projects2/current-project])
        build-actions   (subscribe [:projects2/build-actions])
        upgrade-actions (subscribe [:projects2/upgrade-actions])]
    [:section {:class-name "project-settings-section"}
     [section-header 5 "Actions"]
     [:div {:class "step-info"} "Potential actions to increase access to services. Planwise will use these to explore and recommend the best alternatives."]
     [project-setting-title "account_balance" "Available budget"]
     [current-project-input "" [:config :actions :budget] "number" "$" "" {:disabled read-only :class "project-setting"}]
     [m/TextFieldHelperText {:persistent true} "Planwise will keep explored scenarios below this maximum budget"]

     [project-setting-title "domain" "Building a new provider..."]
     [listing-actions {:read-only?  read-only
                       :action-name :build
                       :list        @build-actions}]

     [project-setting-title "arrow_upward" "Upgrading a provider so that it can satisfy demand would cost..."]
     [current-project-input "" [:config :actions :upgrade-budget] "number" "$" "" {:disabled read-only :class "project-setting"}]

     [project-setting-title "add" "Increase the capactiy of a provider by..."]
     [listing-actions {:read-only?   read-only
                       :action-name :upgrade
                       :list        @upgrade-actions}]]))

(defn- current-project-step-review
  [read-only]
  (let [current-project (subscribe [:projects2/current-project])
        algorithms      (rf/subscribe [:coverage/supported-algorithms])
        providers       (subscribe [:providers-set/dropdown-options])
        provider-set-id        (:provider-set-id @current-project)
        provider        (first (filter #(= provider-set-id (:value %)) @providers))
        algorithm       (get-in @algorithms [(keyword (:coverage-algorithm @current-project))])
        criteria        (first (vals (:criteria algorithm)))
        coverage-amount (first (vals (get-in @current-project [:config :coverage :filter-options])))
        sources         (subscribe [:sources/list])
        source          (first (filter #(= (:source-set-id @current-project) (:id %)) @sources))
        budget          (get-in @current-project [:config :actions :budget])
        workload        (get-in @current-project [:config :providers :capacity])
        consumers-unit        (get-in @current-project [:config :demographics :unit-name])
        capacities       (get-in @current-project [:config :actions :build])]
    (dispatch [:sources/load])
    (dispatch [:providers-set/load-providers-set])
    [:section {:class "project-settings-section"}
     [section-header 6 "Review"]
     [:div {:class "step-info"} "After this step the system will search for different improvements scenarios based on the given parameters. Once started, the process will continue even if you leave the site. From the dashboard you will be able to see the scenarios found so far, pause the search and review the performed work."]
     (if (some? provider)
       [project-setting-title "location_on" (:label provider)]
       [project-setting-title "warning" "The provider dataset field in the \"providers\" tab is needed"])
     (if (some? budget)
       [project-setting-title "account_balance" (str "K " budget)]
       [project-setting-title "warning" "The budget field in the \"actions\" tab is needed"])
     (if (some? source)
       [project-setting-title "people" (:name source)]
       [project-setting-title "warning" "The \"consumers\" tab information is needed"])
     (if (and (some? coverage-amount) (some? provider-set-id))
       [project-setting-title "directions" (str
                                            (:label (first (filter #(= coverage-amount (:value %)) (:options criteria))))
                                            " of "
                                            (:label (first (vals (:criteria algorithm)))))]
       [project-setting-title "warning" "The \"providers\" and \"coverage\" tabs information is needed"])
     (map (fn [action]
            [project-setting-title "info" (join " " ["A facility with a capacity of"
                                                     (:capacity action)
                                                     "will provide service for"
                                                     (* (:capacity action) workload)
                                                     (or (not-empty consumers-unit) "consumers units")
                                                     " per year"])]) capacities)]))


(def map-preview-size {:width 373 :height 278})

(defn current-project-settings-view
  [{:keys [read-only step sections]}]
  (let [current-project (subscribe [:projects2/current-project])
        build-actions   (subscribe [:projects2/build-actions])
        upgrade-actions (subscribe [:projects2/upgrade-actions])
        tags            (subscribe [:projects2/tags])
        regions         (subscribe [:regions/list])]
    (fn [{:keys [read-only step]}]
      (let [project @current-project
            step-data       (first (filter #(= (:step %) step) sections))
            bbox            (:bbox (first (filter #(= (:id %) (:region-id @current-project)) @regions)))
            region-geo      (subscribe [:regions/preview-geojson (:region-id @current-project)])
            preview-map-url (if (:region-id @current-project) (static-image @region-geo map-preview-size))]
        (dispatch [:regions/load-regions-with-preview [(:region-id @current-project)]])
        [m/Grid {:class-name "wizard"}

         [m/GridCell {:span 12 :class-name "steps"}
          (map-indexed (fn [i iteration-step]
                         [:a {:key i
                              :class-name (join " " [(if (= (:step iteration-step) step) "active") (if (s/valid? (:spec iteration-step) project) "complete")])
                              :href (routes/projects2-show-with-step {:id (:id project) :step (:step iteration-step)})}
                          (if (s/valid? (:spec iteration-step) project) [m/Icon "done"] [:i (inc i)])
                          [:div (:title iteration-step)]]) sections)]
         [m/GridCell {:span 6}
          [:form.vertical
           (if (nil? step-data)
             (dispatch [:projects2/infer-step @current-project])
             ((:component step-data) read-only))]]
         [m/GridCell {:span 6}
          [:div.map
           (if (some? bbox)
             [l/map-widget {:position (mapping/bbox-center bbox)
                            :controls []
                            :initial-bbox bbox}
              mapping/default-base-tile-layer
              [:geojson-layer {:data  @region-geo}
               :group {:pane "tilePane"}
               :lat-fn (fn [polygon-point] (:lat polygon-point))
               :lon-fn (fn [polygon-point] (:lon polygon-point))
               :color :orange
               :stroke true]])]]]))))


(defn edit-current-project
  []
  (let [page-params       (subscribe [:page-params])
        current-project (subscribe [:projects2/current-project])
        delete?         (r/atom false)
        hide-dialog     (fn [] (reset! delete? false))
        sections        [{:step "goal" :title "Goal" :component current-project-step-goal :spec :planwise.model.project/goal-step :next-step "consumers"}
                         {:step "consumers" :title "Consumers" :component current-project-step-consumers :spec :planwise.model.project/consumers-step :next-step "providers"}
                         {:step "providers" :title "Providers" :component current-project-step-providers :spec :planwise.model.project/providers-step :next-step "coverage"}
                         {:step "coverage" :title "Coverage" :component current-project-step-coverage :spec :planwise.model.project/coverage-step :next-step "actions"}
                         {:step "actions" :title "Actions" :component current-project-step-actions :spec :planwise.model.project/actions-step :next-step "review"}
                         {:step "review" :title "Review" :component current-project-step-review :spec :planwise.model.project/review-step :next-step nil}]]
    (fn []
      [ui/fixed-width (common2/nav-params)
       [ui/panel {}
        [current-project-settings-view {:read-only false :step (:step @page-params) :sections sections}]

        [:div {:class-name "project-settings-actions"}
         (let [previous-step (:step (first (filter #(= (:next-step %) (:step @page-params)) sections)))]
           (if (nil? previous-step)
             [project-delete-button delete?]
             [project-back-button @current-project previous-step]))
         [project-next-step-button @current-project (:next-step (first (filter #(= (:step %) (:step @page-params)) sections)))]]]
       [delete-project-dialog {:open? @delete?
                               :cancel-fn hide-dialog
                               :delete-fn #(dispatch [:projects2/delete-project (:id @current-project)])}]])))
