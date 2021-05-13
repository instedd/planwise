(ns planwise.client.scenarios.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [crate.core :as crate]
            [re-com.core :as rc]
            [leaflet.core :as l]
            [clojure.string :refer [join split capitalize]]
            [planwise.client.config :as config]
            [planwise.client.scenarios.db :as db]
            [planwise.client.dialog :refer [dialog]]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [planwise.client.styles :as styles]
            [planwise.client.mapping :as mapping]
            [planwise.client.scenarios.edit :as edit]
            [planwise.client.scenarios.changeset :as changeset]
            [planwise.client.components.common :as common]
            [planwise.client.components.common2 :as common2]
            [planwise.client.utils :as utils]
            [planwise.client.ui.rmwc :as m]
            [planwise.common :refer [get-demand-unit get-provider-unit get-capacity-unit]]))


(defn raise-alert
  [{:keys [changeset]} {:keys [causes id]}]
  (let [message ""]
    [:div.raise-alert
     [:div.card-message
      [:div.content
       [:h2.mdc-dialog__header__title "Oops...  something went wrong"]
       [:h3 message]]
      (when id
        [m/Button  {:class-name "bottom-button"
                    :on-click #(dispatch [:scenarios/delete-change id])}
         "Remove last change"])
      [m/Button {:class-name "bottom-button"
                 :on-click #(dispatch [:projects2/project-settings])}
       "Go back to project settings"]]]))


(defn- provider-has-change?
  [provider]
  (some? (:change provider)))

(defn- popup-connected-button
  [label callback]
  (let [button (crate/html [:button label])]
    (.addEventListener button "click" callback)
    button))

(defn- show-provider
  [{:keys [read-only? demand-unit capacity-unit]} {:keys [change matches-filters name capacity free-capacity required-capacity satisfied-demand unsatisfied-demand reachable-demand] :as provider}]
  (let [format-number   (fnil utils/format-number 0)
        change* (if (some? change)
                  change
                  (db/new-action provider (if (not matches-filters) :upgrade :increase)))]
    (crate/html
     [:div
      [:h3 name]
      [:p (str "Capacity: " (utils/format-number capacity) " " capacity-unit)]
      [:p (str "Unsatisfied demand: " (utils/format-number unsatisfied-demand))]
      [:p (str "Required capacity: " (utils/format-number (Math/ceil required-capacity)))]
      (when (or matches-filters change)
        [:p (str "Satisfied demand: " (utils/format-number satisfied-demand))])
      (when (or matches-filters change)
        [:p (str "Free capacity: " (utils/format-number (Math/floor free-capacity)))])
      [:p (str (capitalize demand-unit) " under geographic coverage: " (utils/format-number (Math/ceil reachable-demand)))]
      (when-not read-only?
        (popup-connected-button
         (cond
           (some? change)        "Edit"
           (not matches-filters) "Upgrade"
           :else                 "Increase")
         #(dispatch [:scenarios/edit-change (assoc provider :change change*)])))])))

(defn label-for-suggestion
  [suggestion state]
  (let [new-provider? (= state :new-provider)
        action        (cond
                        new-provider?                 :create
                        (:matches-filters suggestion) :increase
                        :else                         :upgrade)]
    (case action
      :create   "Create"
      :upgrade  "Upgrade"
      :increase "Increase")))

(defn- button-for-suggestion
  [label suggestion]
  (popup-connected-button label #(dispatch [:scenarios/edit-suggestion suggestion])))

(defn- show-suggested-provider
  [{:keys [demand-unit capacity-unit]} {:keys [action-capacity action-cost coverage name ranked] :as suggestion} state]
  (let [new-provider? (= state :new-provider)]
    (crate/html
     [:div
      [:h3 (if name name (str "Suggestion " ranked))]
      [:p (str "Needed capacity : " (utils/format-number (Math/ceil action-capacity)) " " capacity-unit)]
      (when new-provider?
        [:p (str "Expected demand to satisfy : " (utils/format-number coverage) " " demand-unit)])
      (when-not new-provider?
        (when action-cost
          [:p (str "Investment according to project configuration : " (utils/format-number action-cost))]))
      (button-for-suggestion (label-for-suggestion suggestion state) suggestion)])))

(defn- show-source
  [{{:keys [name initial-quantity quantity]} :elem :as source}]
  (str "<b>" (utils/escape-html (str name)) "</b>"
       "<br> Original quantity: " (.toFixed (or initial-quantity 0) 2)
       "<br> Current quantity: " (.toFixed (or quantity 0) 2)))

(defn- to-indexed-map
  [coll]
  (map-indexed (fn [idx elem] {:elem elem :index idx}) coll))

(defn- get-percentage
  [total relative]
  (* (/ relative total) 100))

(defn- get-marker-class-for-provider
  [{:keys [satisfied-demand unsatisfied-demand capacity free-capacity]}]
  (cond
    (> (get-percentage capacity free-capacity) 10)              "idle-capacity"
    (and (>= free-capacity 0) (zero? unsatisfied-demand))       "at-capacity"
    (< (get-percentage satisfied-demand unsatisfied-demand) 10) "small-unsatisfied"
    (< (get-percentage satisfied-demand unsatisfied-demand) 30) "mid-unsatisfied"
    (> unsatisfied-demand 0)                                    "unsatisfied"
    :else                                                       "unsatisfied"))

(defn- icon-function
  [{:keys [id change matches-filters required-capacity satisfied-demand unsatisfied-demand capacity free-capacity] :as provider} selected-provider]
  {:className
   (join " " ["leaflet-circle-icon "
              (cond
                (= id (:id selected-provider)) "selected"
                (and (not change)
                     (not matches-filters)) "not-matching"
                :else (get-marker-class-for-provider provider))
              (when (provider-has-change? provider)
                "leaflet-circle-for-change")
              (when (some? change)
                (str "leaflet-" (:action change)))])})

(defn- suggestion-icon-fn
  [suggestion selected-suggestion classname]
  {:className
   (join " "
         [(if (= suggestion selected-suggestion)
            "leaflet-suggestion-icon selected"
            "leaflet-suggestion-icon")
          classname])})

(defn simple-map
  [_ _ _ _ _]
  (let [selected-provider   (subscribe [:scenarios.map/selected-provider])
        selected-suggestion (subscribe [:scenarios.map/selected-suggestion])
        suggested-locations (subscribe [:scenarios.new-provider/suggested-locations])
        all-providers       (subscribe [:scenarios/all-providers])
        position            (r/atom mapping/map-preview-position)
        zoom                (r/atom 3)
        add-point           (fn [lat lon] (dispatch [:scenarios/create-provider {:lat lat :lon lon}]))
        use-providers-clustering false
        providers-layer-type     (if use-providers-clustering :cluster-layer :marker-layer)]
    (fn [{:keys [bbox] :as project} {:keys [changeset raster sources-data] :as scenario} state error read-only?]
      (let [demand-unit             (get-demand-unit project)
            provider-unit           (get-provider-unit project)
            capacity-unit           (get-capacity-unit project)
            suggestion-classname    (if (= state :new-provider)
                                      "leaflet-suggestion-new-provider"
                                      "leaflet-suggestion-new-improvement")
            indexed-providers       (to-indexed-map @all-providers)
            indexed-sources         (to-indexed-map sources-data)
            pending-demand-raster   raster
            suggested-locations     @suggested-locations
            selected-suggestion     @selected-suggestion
            sources-layer           [:marker-layer {:points indexed-sources
                                                    :shape :square
                                                    :lat-fn #(get-in % [:elem :lat])
                                                    :lon-fn #(get-in % [:elem :lon])
                                                    :icon-fn #(let [source (:elem %)
                                                                    quantity-initial (:initial-quantity source)
                                                                    quantity-current (:quantity source)
                                                                    ratio (if (pos? quantity-initial) (/ quantity-current quantity-initial) 0)
                                                                    classname (cond
                                                                                (= 0 quantity-initial) "leaflet-square-icon-gray"
                                                                                (<= ratio 0.25) "leaflet-square-icon-green"
                                                                                (< 0.25 ratio 0.5) "leaflet-sqaure-icon-yellow"
                                                                                (<= 0.5 ratio 0.75) "leaflet-square-icon-orange"
                                                                                (> ratio 0.75) "leaflet-square-icon-red")]
                                                                {:className classname})
                                                    :popup-fn #(show-source %)}]
            selected-provider-layer [:geojson-layer {:data  (:coverage-geom @selected-provider)
                                                     :group {:pane "tilePane"}
                                                     :lat-fn (fn [polygon-point] (:lat polygon-point))
                                                     :lon-fn (fn [polygon-point] (:lon polygon-point))
                                                     :color :orange
                                                     :stroke true}]
            suggestions-layer       [:marker-layer {:points (map #(assoc % :open? (= % selected-suggestion)) suggested-locations)
                                                    :lat-fn #(get-in % [:location :lat])
                                                    :lon-fn #(get-in % [:location :lon])
                                                    :popup-fn #(show-suggested-provider {:demand-unit demand-unit :capacity-unit capacity-unit} % state)
                                                    :icon-fn #(suggestion-icon-fn % selected-suggestion suggestion-classname)
                                                    :mouseover-fn (fn [suggestion]
                                                                    (dispatch [:scenarios.map/select-suggestion suggestion]))
                                                    :mouseout-fn  (fn [suggestion]
                                                                    (dispatch [:scenarios.map/unselect-suggestion suggestion]))}]
            providers-layer [providers-layer-type {:points (map #(assoc % :open? (= % @selected-provider)) @all-providers)
                                                   :lat-fn #(get-in % [:location :lat])
                                                   :lon-fn #(get-in % [:location :lon])
                                                   :icon-fn #(icon-function % @selected-provider)
                                                   :popup-fn     #(show-provider {:read-only? read-only?
                                                                                  :demand-unit demand-unit
                                                                                  :capacity-unit capacity-unit} %)
                                                   :mouseover-fn (fn [provider]
                                                                   (dispatch [:scenarios.map/select-provider provider]))
                                                   :mouseout-fn  (fn [provider]
                                                                   (dispatch [:scenarios.map/unselect-provider provider]))}]]
        [:div.map-container (when error {:class "gray-filter"})
         [l/map-widget {:zoom @zoom
                        :position @position
                        :on-position-changed #(reset! position %)
                        :on-zoom-changed #(reset! zoom %)
                        :on-click (cond (= state :new-provider) add-point)
                        :controls [:legend]
                        :initial-bbox bbox
                        :pointer-class (cond (= state :new-provider) "crosshair-pointer")
                        :provider-unit provider-unit}
          mapping/default-base-tile-layer
          (when pending-demand-raster
            [:wms-tile-layer
             {:url config/mapserver-url
              :transparent true
              :layers "scenario"
              :DATAFILE (str pending-demand-raster ".map")
              :format "image/png"
              :opacity 0.6}])
          sources-layer
          providers-layer
          (when @selected-provider selected-provider-layer)
          (when suggested-locations suggestions-layer)]]))))

(defn- create-new-scenario
  [current-scenario]
  [m/Button {:class-name "btn-create"
             :on-click #(dispatch [:scenarios/copy-scenario (:id current-scenario)])}
   "Create new scenario from here"])

(defn- format-percentage
  [num denom]
  (utils/format-percentage (/ num denom) 2))

(defn initial-scenario-panel
  [{:keys [name demand-coverage state]}]
  (let [source-demand   (subscribe [:scenarios.current/source-demand])
        current-project (subscribe [:projects2/current-project])
        demand-unit     (get-demand-unit @current-project)]
    [:div
     [:div {:class-name "section"}
      [:h1 {:class-name "title-icon"} name]]
     [:hr]
     [:div {:class-name "section"}
      [:h1 {:class-name "large"}
       [:small (str "Initial " demand-unit " coverage")]
       (cond
         (= "pending" state) "loading..."
         :else (str (utils/format-number demand-coverage) " (" (format-percentage demand-coverage @source-demand) ")"))]
      [:p {:class-name "grey-text"}
       (str "of a total of " (utils/format-number @source-demand))]]]))

(defn scenario-info
  [view-state current-scenario]
  (let [{:keys [name label effort demand-coverage source-demand population-under-coverage increase-coverage state]} current-scenario
        current-project (subscribe [:projects2/current-project])
        analysis-type   (get-in @current-project [:config :analysis-type])
        demand-unit     (get-demand-unit @current-project)]
    [:div
     [:div {:class-name "section"}
      [:h1 {:class-name "title-icon"} name]]
     [edit/scenario-settings view-state]
     [:hr]
     [:div {:class-name "section"}
      [:h1 {:class-name "large"}
       [:small (str "Increase in " demand-unit " coverage")]
       (cond
         (= "pending" state) "loading..."
         :else               (str (utils/format-number increase-coverage) " (" (format-percentage increase-coverage source-demand) ")"))]
      [:p {:class-name "grey-text"}
       (cond
         (= "pending" state) "to a total of"
         :else               (str "to a total of " (utils/format-number demand-coverage) " (" (format-percentage demand-coverage source-demand) ")"))]]
     [:div {:class-name "section"}
      [:h1 {:class-name "large"}
       [:small (str "Total " demand-unit " under geographic coverage")]
       (cond
         (= "pending" state) "loading..."
         :else  (utils/format-number population-under-coverage))]]
     [:div {:class-name "section"}
      [:h1 {:class-name "large"}
       [:small "Effort required"]
       (utils/format-effort effort analysis-type)]]
     [:hr]]))

(defn suggested-locations-list
  [props suggested-locations]
  [:<>
   [:div.section
    [:h1.title-icon "Suggestion list"]]
   [:div..fade]
   [changeset/suggestion-listing-component props suggested-locations]
   [:div.fade.inverted]])

(defn new-provider-unit?
  [view-state]
  (or (= view-state :new-provider)
      (= view-state :new-intervention)))

(defn suggestions-view
  [{:keys [project locations]}]
  (let [demand-unit   (get-demand-unit project)
        capacity-unit (get-capacity-unit project)
        provider-unit (get-provider-unit project)]
    [:<>
     [:div {:class-name "suggestion-list"}
      [edit/create-new-action-component {:type :new-unit
                                         :provider-unit provider-unit
                                         :on-click #(dispatch [:scenarios/close-suggestions])}]]
     [suggested-locations-list {:demand-unit demand-unit
                                :capacity-unit capacity-unit}
      locations]]))

(defn scenario-view
  [{:keys [view-state scenario computing? computing-best-locations? providers project error]}]
  (let [provider-unit (get-provider-unit project)
        demand-unit   (get-demand-unit project)
        capacity-unit (get-capacity-unit project)
        target        (if computing-best-locations? :computing-best-locations :computing-best-improvements)
        action-type   (cond computing? :computing
                            (new-provider-unit? view-state) :new-unit
                            :else :default)
        action-event  (cond computing? [:scenarios.new-action/abort-fetching-suggestions target]
                            (new-provider-unit? view-state) [:scenarios/close-suggestions]
                            (= view-state :show-options-to-create-provider) [:scenarios/close-create-suggestions-menu]
                            :else [:scenarios/show-create-suggestions-menu])]
    [:<>
     [:div
      [scenario-info view-state scenario]
      [edit/create-new-action-component {:type action-type
                                         :provider-unit provider-unit
                                         :on-click #(dispatch action-event)}]
      (if computing?
        [:div {:class-name "info-computing-best-location"}
         [:small (if computing-best-locations?
                   "Computing best locations ..."
                   "Computing best improvements...")]])]
     (if error
       [raise-alert scenario error]
       [:<>
        [:div {:class-name "fade"}]
        [changeset/listing-component {:demand-unit demand-unit
                                      :capacity-unit capacity-unit}
         providers]
        [:div {:class-name "fade inverted"}]])]))

(defn side-panel-view-2
  [current-scenario error]
  (let [computing-best-locations?    (subscribe [:scenarios.new-provider/computing-best-locations?])
        computing-best-improvements? (subscribe [:scenarios.new-intervention/computing-best-improvements?])
        suggested-locations          (subscribe [:scenarios.new-provider/suggested-locations])
        view-state                   (subscribe [:scenarios/view-state])
        providers-from-changeset     (subscribe [:scenarios/providers-from-changeset])
        current-project              (subscribe [:projects2/current-project])
        computing-suggestions?       (or @computing-best-locations? @computing-best-improvements?)]
    (if @suggested-locations
      [suggestions-view {:locations @suggested-locations
                         :project @current-project}]
      [scenario-view {:view-state @view-state
                      :scenario current-scenario
                      :computing? computing-suggestions?
                      :computing-best-locations? @computing-best-locations?
                      :providers @providers-from-changeset
                      :project @current-project
                      :error error}])))

(defn side-panel-view
  [current-scenario error read-only?]
  [:<>
   (if read-only?
     [initial-scenario-panel current-scenario]
     [side-panel-view-2 current-scenario error])
   [create-new-scenario current-scenario]])

(defn scenario-line-info
  [{:keys [name effort source-demand increase-coverage] :as current-scenario}]
  (let [current-project (subscribe [:projects2/current-project])
        analysis-type   (get-in @current-project [:config :analysis-type])
        demand-unit     (get-demand-unit @current-project)]
    [:div.section.actions-table-header
     [:div.actions-table-scenario-info
      [:div [:h3 name]]
      [:div [:h3.grey-text (str "Increase in " demand-unit " coverage " (utils/format-number increase-coverage) " (" (format-percentage increase-coverage source-demand) ")")]]
      [:div [:h3.grey-text (str "Effort required " (utils/format-effort effort analysis-type))]]]]))

(defn table-actions-view
  [current-scenario]
  (let [current-project          (subscribe [:projects2/current-project])
        providers-from-changeset (subscribe [:scenarios/providers-from-changeset])
        demand-unit              (get-demand-unit @current-project)
        capacity-unit            (get-capacity-unit @current-project)]
    [:<>
     [scenario-line-info current-scenario]
     [changeset/table-component {:demand-unit demand-unit
                                 :capacity-unit capacity-unit}
      @providers-from-changeset]
     [:div
      [create-new-scenario current-scenario]]]))

(defn sidebar-expand-button
  [expanded-sidebar?]
  [:div.sidebar-expand-button
   [:div.sidebar-button {:on-click #(dispatch [(if expanded-sidebar?
                                                 :scenarios/show-scenario
                                                 :scenarios/show-actions-table)])}
    [:div.sidebar-button-inner
     [m/Icon {:strategy "ligature"
              :use (if expanded-sidebar? "arrow_left" "arrow_right")}]]]])

(defn display-current-scenario
  [current-project {:keys [id] :as current-scenario}]
  (let [read-only?  (subscribe [:scenarios/read-only?])
        state       (subscribe [:scenarios/view-state])
        error       (subscribe [:scenarios/error])
        export-providers-button [:a {:class "mdc-fab disable-a"
                                     :id "main-action"
                                     :href (str "/api/scenarios/" id "/csv")}
                                 [m/Icon {:class "material-icons  center-download-icon"} "get_app"]]]
    (fn [current-project current-scenario]
      (let [expanded-sidebar? (= @state :show-actions-table)]
        [ui/full-screen (merge (common2/nav-params)
                               {:main-prop {:style {:position :relative}}
                                :main [simple-map current-project current-scenario @state @error @read-only?]
                                :title [:ul {:class-name "breadcrumb-menu"}
                                        [:li [:a {:href (routes/projects2-show {:id (:id current-project)})} (:name current-project)]]
                                        [:li [m/Icon {:strategy "ligature" :use "keyboard_arrow_right"}]]
                                        [:li (:name current-scenario)]]
                                :sidebar-prop {:class [(if expanded-sidebar? :expanded-sidebar :compact-sidebar)]}}
                               (when-not expanded-sidebar?
                                 {:action export-providers-button}))
         (if expanded-sidebar?
           [table-actions-view current-scenario]
           [side-panel-view current-scenario @error @read-only?])
         [edit/rename-scenario-dialog]
         [edit/changeset-dialog current-project current-scenario]
         [edit/delete-scenario-dialog @state current-scenario]
         [sidebar-expand-button expanded-sidebar?]]))))

(defn scenarios-page
  []
  (let [current-scenario (subscribe [:scenarios/current-scenario])
        current-project  (subscribe [:projects2/current-project])]
    (fn []
      (when (and @current-scenario @current-project)
        [display-current-scenario @current-project @current-scenario]))))
