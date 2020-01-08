(ns planwise.client.scenarios.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [crate.core :as crate]
            [re-com.core :as rc]
            [leaflet.core :as l]
            [clojure.string :refer [join split]]
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
            [planwise.client.ui.rmwc :as m]))


(defn raise-alert
  [project {:keys [changeset]} {:keys [causes id]}]
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
  [read-only? unit-name {:keys [change matches-filters name capacity free-capacity required-capacity satisfied-demand unsatisfied-demand reachable-demand] :as provider}]
  (let [format-number   (fnil utils/format-number 0)
        change* (if (some? change)
                  change
                  (db/new-action provider (if (not matches-filters) :upgrade :increase)))]
    (crate/html
     [:div
      [:h3 name]
      [:p (str "Capacity: " (utils/format-number capacity))]
      [:p (str "Unsatisfied demand: " (utils/format-number unsatisfied-demand))]
      [:p (str "Required capacity: " (utils/format-number (Math/ceil required-capacity)))]
      (when (or matches-filters change)
        [:p (str "Satisfied demand: " (utils/format-number satisfied-demand))])
      (when (or matches-filters change)
        [:p (str "Free capacity: " (utils/format-number (Math/floor free-capacity)))])
      [:p (str unit-name " under geographic coverage: " (utils/format-number (Math/ceil reachable-demand)))]
      (when-not read-only?
        (popup-connected-button
         (cond
           (some? change)        "Edit provider"
           (not matches-filters) "Upgrade provider"
           :else                "Increase provider")
         #(dispatch [:scenarios/edit-change (assoc provider :change change*)])))])))

(defn action-for-suggestion
  [suggestion state]
  (let [new-provider? (= state :new-provider)
        action        (cond
                        new-provider?                 :create
                        (:matches-filters suggestion) :increase
                        :else                         :upgrade)]
    (case action
      :create   {:label    "Create new provider"
                 :callback #(dispatch [:scenarios/create-provider (:location suggestion)])}
      :upgrade  {:label    "Upgrade provider"
                 :callback #(dispatch [:scenarios/edit-change suggestion])}
      :increase {:label    "Increase provider"
                 :callback #(dispatch [:scenarios/edit-change suggestion])})))

(defn- button-for-suggestion
  [action]
  (popup-connected-button (:label action) (:callback action)))

(defn- show-suggested-provider
  [suggestion state]
  (let [new-provider? (= state :new-provider)]
    (crate/html
     [:div
      [:p (str "Suggestion:" (:ranked suggestion))]
      [:p (str "Needed capacity : " (utils/format-number (:action-capacity suggestion)))]
      (when new-provider?
        [:p (str "Expected demand to satisfy : " (utils/format-number (:coverage suggestion)))])
      (when-not new-provider?
        (let [action-cost (:action-cost suggestion)]
          (when action-cost
            [:p (str "Investment according to project configuration : " (utils/format-number action-cost))])))
      (button-for-suggestion (action-for-suggestion suggestion state))])))

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
   (str
    "leaflet-circle-icon "
    (cond
      (= id  (:id selected-provider)) "selected"
      (and (not change)
           (not matches-filters)) "not-matching"
      :else (get-marker-class-for-provider provider))
    " "
    (when (provider-has-change? provider)
      "leaflet-circle-for-change"))})

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
        unit-name           (get-in project [:config :demographics :unit-name])]
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
            suggestions-layer       [:marker-layer {:points suggested-locations
                                                    :lat-fn #(get-in % [:location :lat])
                                                    :lon-fn #(get-in % [:location :lon])
                                                    :popup-fn   #(show-suggested-provider % state)
                                                    :icon-fn #(suggestion-icon-fn % selected-suggestion)
                                                    :mouseover-fn (fn [suggestion]
                                                                    (dispatch [:scenarios.map/select-suggestion suggestion]))
                                                    :mouseout-fn  (fn [suggestion]
                                                                    (dispatch [:scenarios.map/unselect-suggestion suggestion]))}]
            providers-layer [providers-layer-type {:points @all-providers
                                                   :lat-fn #(get-in % [:location :lat])
                                                   :lon-fn #(get-in % [:location :lon])
                                                   :icon-fn #(icon-function % @selected-provider)
                                                   :popup-fn     #(show-provider read-only? unit-name %)
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
                        :pointer-class (cond (= state :new-provider) "crosshair-pointer")}
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
    (fn [{:keys [name demand-coverage state]} unit-name]
      [:div
       [:div {:class-name "section"}
        [:h1 {:class-name "title-icon"} name]]
       [:hr]
       [:div {:class-name "section"}
        [:h1 {:class-name "large"}
         [:small (str "Initial " unit-name " coverage")]
         (cond
           (= "pending" state) "loading..."
           :else (str (utils/format-number demand-coverage) " (" (format-percentage demand-coverage @source-demand) ")"))]
        [:p {:class-name "grey-text"}
         (str "of a total of " (utils/format-number @source-demand))]]])))

(defn side-panel-view
  [_ _]
  (let [computing-best-locations?    (subscribe [:scenarios.new-provider/computing-best-locations?])
        computing-best-improvements? (subscribe [:scenarios.new-intervention/computing-best-improvements?])
        suggested-locations          (subscribe [:scenarios.new-provider/suggested-locations])
        view-state                   (subscribe [:scenarios/view-state])
        source-demand                (subscribe [:scenarios.current/source-demand])
        population-under-coverage    (subscribe [:scenarios.current/population-under-coverage])]
    (fn [{:keys [name label investment demand-coverage increase-coverage state]} unit-name]
      [:div
       (cond (not @suggested-locations)
             [:div
              [:div {:class-name "section"}
               [:h1 {:class-name "title-icon"} name]]
              [edit/scenario-settings @view-state]
              [:hr]
              [:div {:class-name "section"}
               [:h1 {:class-name "large"}
                [:small (str "Increase in " unit-name " coverage")]
                (cond
                  (= "pending" state) "loading..."
                  :else               (str increase-coverage " (" (format-percentage increase-coverage @source-demand) ")"))]
               [:p {:class-name "grey-text"}
                (cond
                  (= "pending" state) "to a total of"
                  :else               (str "to a total of " (utils/format-number demand-coverage) " (" (format-percentage demand-coverage @source-demand) ")"))]]
              [:div {:class-name "section"}
               [:h1 {:class-name "large"}
                [:small (str "Total " unit-name " under geographic coverage")]
                (cond
                  (= "pending" state) "loading..."
                  :else  @population-under-coverage)]]
              [:div {:class-name "section"}
               [:h1 {:class-name "large"}
                [:small "Investment required"]
                "K " (utils/format-number investment)]]
              [:hr]]
             :else
             [:div
              [:div {:class-name "section"}
               [:h1 {:class-name "title-icon"} "Suggestion list"]
               [changeset/suggestion-listing-component @suggested-locations state show-suggested-provider]]])
       [edit/create-new-action-component @view-state (or @computing-best-locations? @computing-best-improvements?)]
       (if (or @computing-best-locations? @computing-best-improvements?)
         [:div {:class-name "info-computing-best-location"}
          [:small (if @computing-best-locations?
                    "Computing best locations ..."
                    "Computing best improvements...")]])])))

(defn display-current-scenario
  [current-project {:keys [id] :as current-scenario}]
  (let [read-only? (subscribe [:scenarios/read-only?])
        state      (subscribe [:scenarios/view-state])
        error      (subscribe [:scenarios/error])
        providers-from-changeset (subscribe [:scenarios/providers-from-changeset])
        unit-name  (get-in current-project [:config :demographics :unit-name])
        export-providers-button [:a {:class "mdc-fab disable-a"
                                     :id "main-action"
                                     :href (str "/api/scenarios/" id "/csv")}
                                 [m/Icon {:class "material-icons  center-download-icon"} "get_app"]]]
    (fn [current-project current-scenario]
      [ui/full-screen (merge (common2/nav-params)
                             {:main-prop {:style {:position :relative}}
                              :main [simple-map current-project current-scenario @state @error @read-only?]
                              :title [:ul {:class-name "breadcrumb-menu"}
                                      [:li [:a {:href (routes/projects2-show {:id (:id current-project)})} (:name current-project)]]
                                      [:li [m/Icon {:strategy "ligature" :use "keyboard_arrow_right"}]]
                                      [:li (:name current-scenario)]]
                              :action export-providers-button})
       (if @read-only?
         [initial-scenario-panel current-scenario unit-name]
         [side-panel-view current-scenario unit-name])
       [:div (when-not @error {:class-name "fade"})]
       (if @error
         [raise-alert current-project current-scenario @error]
         [changeset/listing-component @providers-from-changeset])
       [:div (when-not @error {:class-name "fade inverted"})]
       [create-new-scenario current-scenario]
       [edit/rename-scenario-dialog]
       [edit/changeset-dialog current-project current-scenario]
       [edit/delete-scenario-dialog @state current-scenario]])))

(defn scenarios-page
  []
  (let [page-params (subscribe [:page-params])
        current-scenario (subscribe [:scenarios/current-scenario])
        current-project  (subscribe [:projects2/current-project])]
    (r/create-class
     {:reagent-render
      (fn []
        (let [{:keys [id project-id]} @page-params]
          (cond
            (not= id (:id @current-scenario)) (dispatch [:scenarios/get-scenario id])
            (not= project-id (:id @current-project)) (dispatch [:projects2/get-project project-id])
            (not= project-id (:project-id @current-scenario)) (dispatch [:scenarios/scenario-not-found])
            :else
            [display-current-scenario @current-project @current-scenario])))

      :component-will-unmount
      (fn []
        (dispatch [:scenarios/clear-current-scenario]))})))
