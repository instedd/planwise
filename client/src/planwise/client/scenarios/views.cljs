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
  [suggestion selected-suggestion]
  {:className
   (if (= suggestion selected-suggestion) "leaflet-suggestion-icon selected" "leaflet-suggestion-icon")})

(defn simple-map
  [{:keys [bbox] :as project} scenario state error read-only?]
  (let [selected-provider   (subscribe [:scenarios.map/selected-provider])
        selected-suggestion (subscribe [:scenarios.map/selected-suggestion])
        suggested-locations (subscribe [:scenarios.new-provider/suggested-locations])
        all-providers       (subscribe [:scenarios/all-providers])
        position            (r/atom mapping/map-preview-position)
        zoom                (r/atom 3)
        add-point           (fn [lat lon] (dispatch [:scenarios/create-provider {:lat lat :lon lon}]))
        use-providers-clustering false
        providers-layer-type     (if use-providers-clustering :cluster-layer :marker-layer)
        demand-unit              (get-demand-unit project)
        provider-unit            (get-provider-unit project)
        capacity-unit            (get-capacity-unit project)]
    (fn [{:keys [bbox]} {:keys [changeset raster sources-data] :as scenario} state error]
      (let [indexed-providers       (to-indexed-map @all-providers)
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
                                                    :icon-fn #(suggestion-icon-fn % selected-suggestion)
                                                    :mouseover-fn (fn [suggestion]
                                                                    (dispatch [:scenarios.map/select-suggestion suggestion]))
                                                    :mouseout-fn  (fn [suggestion]
                                                                    (dispatch [:scenarios.map/unselect-suggestion suggestion]))}]
            providers-layer [providers-layer-type {:points @all-providers
                                                   :lat-fn #(get-in % [:location :lat])
                                                   :lon-fn #(get-in % [:location :lon])
                                                   :icon-fn #(icon-function % @selected-provider)
                                                   :popup-fn     #(show-provider {:read-only read-only?
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
  [_ _]
  (let [source-demand (subscribe [:scenarios.current/source-demand])]
    (fn [{:keys [name demand-coverage state]} demand-unit]
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
         (str "of a total of " (utils/format-number @source-demand))]]])))

(defn scenario-info
  [view-state current-scenario demand-unit analysis-type]
  (let [{:keys [name label effort demand-coverage source-demand population-under-coverage increase-coverage state]} current-scenario]
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
  [:div
   [:div {:class-name "section"}
    [:h1 {:class-name "title-icon"} "Suggestion list"]
    [:div {:class-name "fade"}]
    [changeset/suggestion-listing-component props suggested-locations]
    [:div {:class-name "fade inverted"}]]])

(defn side-panel-view-2
  [_ _ _]
  (let [computing-best-locations?    (subscribe [:scenarios.new-provider/computing-best-locations?])
        computing-best-improvements? (subscribe [:scenarios.new-intervention/computing-best-improvements?])
        suggested-locations          (subscribe [:scenarios.new-provider/suggested-locations])
        view-state                   (subscribe [:scenarios/view-state])
        source-demand                (subscribe [:scenarios.current/source-demand])
        population-under-coverage    (subscribe [:scenarios.current/population-under-coverage])
        providers-from-changeset     (subscribe [:scenarios/providers-from-changeset])
        current-project              (subscribe [:projects2/current-project])
        analysis-type                (get-in @current-project [:config :analysis-type])
        demand-unit                  (get-demand-unit @current-project)
        capacity-unit                (get-capacity-unit @current-project)]
    (fn [{:keys [state] :as current-scenario} demand-unit error]
      (let [computing-suggestions?   (or @computing-best-locations? @computing-best-improvements?)
            edit-button              [edit/create-new-action-component @view-state computing-suggestions? @current-project]]
        (if @suggested-locations
          [:<>
           [:div {:class-name "suggestion-list"}
            [edit/create-new-action-component @view-state computing-suggestions? @current-project]]
           [suggested-locations-list {:state state :demand-unit demand-unit :capacity-unit capacity-unit} @suggested-locations]]
          [:<>
           [:div
            [scenario-info @view-state current-scenario demand-unit analysis-type]
            [edit/create-new-action-component @view-state computing-suggestions? @current-project]
            (if computing-suggestions?
              [:div {:class-name "info-computing-best-location"}
               [:small (if @computing-best-locations?
                         "Computing best locations ..."
                         "Computing best improvements...")]])]
           (if error
             [raise-alert current-scenario error]
             [:<>
              [:div {:class-name "fade"}]
              [changeset/listing-component @providers-from-changeset analysis-type]
              [:div {:class-name "fade inverted"}]])])))))

(defn side-panel-view
  [current-scenario demand-unit error read-only?]
  [:<>
   (if read-only?
     [initial-scenario-panel current-scenario demand-unit]
     [side-panel-view-2 current-scenario demand-unit error])
   [create-new-scenario current-scenario]])

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
      (let [demand-unit (get-demand-unit current-project)]
        [ui/full-screen (merge (common2/nav-params)
                               {:main-prop {:style {:position :relative}}
                                :main [simple-map current-project current-scenario @state @error @read-only?]
                                :title [:ul {:class-name "breadcrumb-menu"}
                                        [:li [:a {:href (routes/projects2-show {:id (:id current-project)})} (:name current-project)]]
                                        [:li [m/Icon {:strategy "ligature" :use "keyboard_arrow_right"}]]
                                        [:li (:name current-scenario)]]
                                :action export-providers-button})
         [side-panel-view current-scenario demand-unit @error @read-only?]
         [edit/rename-scenario-dialog]
         [edit/changeset-dialog current-project current-scenario]
         [edit/delete-scenario-dialog @state current-scenario]]))))

(defn scenarios-page
  []
  (let [current-scenario (subscribe [:scenarios/current-scenario])
        current-project  (subscribe [:projects2/current-project])]
    (fn []
      (when (and @current-scenario @current-project)
        [display-current-scenario @current-project @current-scenario]))))
