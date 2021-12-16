(ns planwise.client.scenarios.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [crate.core :as crate]
            [re-com.core :as rc]
            [leaflet.core :as l]
            [clojure.string :refer [join split capitalize]]
            [planwise.client.config :as config]
            [planwise.client.scenarios.db :as db]
            [planwise.client.ui.dialog :refer [dialog]]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [planwise.client.styles :as styles]
            [planwise.client.mapping :as mapping]
            [planwise.client.scenarios.api :as api]
            [planwise.client.scenarios.edit :as edit]
            [planwise.client.scenarios.changeset :as changeset]
            [planwise.client.components.common2 :as common2]
            [planwise.client.utils :as utils]
            [planwise.client.ui.rmwc :as m]
            [planwise.common :refer [get-demand-unit get-provider-unit get-capacity-unit]]))


;;; UI helper functions

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


;;; Layer functions

(defn- popup-connected-button
  [label callback]
  (let [button (crate/html [:button.mdc-button.mdc-button--dense.mdc-button--raised label])]
    (.addEventListener button "click" callback)
    button))


;;; Providers layers

(defn- get-percentage
  [total relative]
  (* (/ relative total) 100))

(defn- provider-has-change?
  [{:keys [change]}]
  (some? change))

(defn- provider-needs-upgrade?
  [{:keys [change matches-filters]}]
  (and (not (some? change)) (not matches-filters)))

(defn- provider-satisfaction
  [{:keys [unsatisfied-demand capacity free-capacity]}]
  (cond
    (> (get-percentage capacity free-capacity) 10)        :excess
    (and (>= free-capacity 0) (zero? unsatisfied-demand)) :covered
    :else                                                 :unsatisfied))

(defn- provider-tooltip
  [{:keys [demand-unit capacity-unit]}
   {:keys [name free-capacity required-capacity unsatisfied-demand] :as provider}]
  (let [has-change?    (provider-has-change? provider)
        satisfaction   (provider-satisfaction provider)
        has-excess?    (= :excess satisfaction)
        covered?       (= :covered satisfaction)
        needs-upgrade? (provider-needs-upgrade? provider)]
    [:div.mdc-typography
     [:h3 name]
     (cond
       (and has-change? has-excess?)
       [:p.excess
        (utils/format-number (Math/floor free-capacity))
        " added " capacity-unit " are not required."
        [:br]
        "Please relocate."]

       (and has-change? covered?)
       [:p.covered
        "All demand covered."]

       has-change?
       [:p.unsatisfied
        (utils/format-number (Math/ceil required-capacity))
        " more " capacity-unit " required "
        [:br]
        "to cover "
        (utils/format-number unsatisfied-demand) " " demand-unit
        " without service."]

       (and (not has-change?) has-excess?)
       [:p.excess
        "All demand covered"
        [:br]
        "with " (utils/format-number (Math/floor free-capacity))
        " idle extra " capacity-unit "."]

       (and (not has-change?) covered?)
       [:p.covered
        "All demand covered, no actions needed."]

       (and (not has-change?) needs-upgrade?)
       [:p.unsatisfied
        "Upgrade and " (utils/format-number (Math/ceil required-capacity))
        " " capacity-unit " required"
        [:br]
        "to provide service to "
        (utils/format-number unsatisfied-demand) " " demand-unit "."]

       :else
       [:p.unsatisfied
        (utils/format-number (Math/ceil required-capacity))
        " " capacity-unit " required to cover "
        [:br]
        (utils/format-number unsatisfied-demand) " " demand-unit
        " without service."])]))

(defn- satisfaction->icon-class
  [provider]
  (case (provider-satisfaction provider)
    :excess      "idle-capacity"
    :covered     "at-capacity"
    :unsatisfied "unsatisfied"
    "unsatisfied"))

(defn- provider-icon-function
  [{:keys [id change matches-filters selected? disabled?] :as provider}]
  (let [has-change?  (provider-has-change? provider)
        base-classes ["marker-provider-icon"
                      (when selected? "selected")
                      (when disabled? "disabled")
                      (satisfaction->icon-class provider)]]
    (if has-change?
      {:html (str "<i class='material-icons'>" (get changeset/action-icons (:action change)) "</i>")
       :className
       (join " " (conj base-classes "for-change"))}
      {:className
       (join " " (conj base-classes (when (not matches-filters) "upgradeable")))})))

(defn- scenario-providers-layer
  [{:keys [project scenario click-fn popup-fn mouseover-fn mouseout-fn]}]
  (let [selected-provider    @(subscribe [:scenarios.map/selected-provider])
        listing-suggestions? @(subscribe [:scenarios/listing-suggestions?])
        searching?           @(subscribe [:scenarios/searching-providers?])
        matching-ids         @(subscribe [:scenarios/search-matching-ids])
        all-providers        @(subscribe [:scenarios/all-providers])
        demand-unit          (get-demand-unit project)
        capacity-unit        (get-capacity-unit project)]
    (into [:feature-group {:key "providers-layer"}]
          (map (fn [{:keys [id location name] :as provider}]
                 (let [selected?    (= id (:id selected-provider))
                       disabled?    (or listing-suggestions?
                                        (and searching? (not (contains? matching-ids id))))
                       marker-props {:key          id
                                     :lat          (:lat location)
                                     :lon          (:lon location)
                                     :icon         (provider-icon-function (assoc provider
                                                                                  :selected? selected?
                                                                                  :disabled? disabled?))
                                     :provider     provider
                                     :zIndexOffset (if (provider-has-change? provider) 3000 2000)}]
                   [:marker (merge marker-props
                                   (when-not disabled?
                                     {:tooltip      (provider-tooltip {:demand-unit   demand-unit
                                                                       :capacity-unit capacity-unit}
                                                                      provider)
                                      :open?        (when selected? (:open? selected-provider))
                                      :hover?       (when selected? (:hover? selected-provider))
                                      :click-fn     click-fn
                                      :popup-fn     popup-fn
                                      :mouseover-fn mouseover-fn
                                      :mouseout-fn  mouseout-fn}))]))
               all-providers))))

(defn- scenario-selected-coverage-layer
  []
  (when-let [selected-coverage @(subscribe [:scenarios.map/selected-coverage])]
    [:geojson-layer {:key       "selected-coverage-layer"
                     :data      selected-coverage
                     :group     {:pane "tilePane"}
                     :className "coverage-polygon"}]))


;;; Suggestions

(defn- label-for-suggestion
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
  [{:keys [demand-unit capacity-unit]}
   {:keys [action-capacity action-cost coverage name ranked] :as suggestion}
   state]
  (let [new-provider? (= state :new-provider)]
    (crate/html
     [:div.mdc-typography
      [:h3 (if name name (str "Suggestion " ranked))]
      [:table
       [:tr
        [:td "Needed capacity : "]
        [:td (str (utils/format-number (Math/ceil action-capacity)) " " capacity-unit)]]
       (when new-provider?
         [:tr
          [:td "Expected demand to satisfy:"]
          [:td (str (utils/format-number coverage) " " demand-unit)]])
       (when (and (not new-provider?) action-cost)
         [:tr
          [:td "Estimated investment:"]
          [:td (utils/format-number action-cost)]])]
      [:div.actions (button-for-suggestion (label-for-suggestion suggestion state) suggestion)]])))

(defn- suggestion-icon-function
  [{:keys [ranked change] :as suggestion} selected-suggestion]
  (let [action-icon (get changeset/action-icons (:action change) "create-provider")]
    {:html (if ranked
             (str "<span>" ranked "</span>")
             (str "<i class='material-icons'>" action-icon "</i>"))
     :className
     (->> ["marker-suggestion-icon"
           (when (= suggestion selected-suggestion) "selected")]
          (filter some?)
          (join " "))}))

(defn- scenario-suggestions-layer
  [{:keys [popup-fn mouseover-fn mouseout-fn]}]
  (let [suggestions         @(subscribe [:scenarios/suggestions])
        selected-suggestion @(subscribe [:scenarios.map/selected-suggestion])]
    (into [:feature-group {:key "suggestions-layer"}]
          (map (fn [{:keys [location name] :as suggestion}]
                 [:marker {:lat          (:lat location)
                           :lon          (:lon location)
                           :icon         (suggestion-icon-function suggestion selected-suggestion)
                           :open?        (= suggestion selected-suggestion)
                           :suggestion   suggestion
                           :popup-fn     popup-fn
                           :mouseover-fn mouseover-fn
                           :mouseout-fn  mouseout-fn
                           :zIndexOffset 4000}])
               suggestions))))


;;; Sources

(defn- source-satisfaction
  [{:keys [quantity initial-quantity]}]
  (let [ratio (if (pos? initial-quantity)
                (/ quantity initial-quantity)
                0)]
    (cond
      (= 0 initial-quantity) :no-demand
      (<= ratio 0.05)        :covered
      (< 0.05 ratio 0.25)    :q1
      (<= 0.25 ratio 0.5)    :q2
      (<= 0.5 ratio 0.75)    :q3
      (> ratio 0.75)         :q4)))

(defn- source-tooltip
  [{:keys [demand-unit]} {:keys [name quantity] :as source}]
  [:div.mdc-typography
   [:h3 name]
   (case (source-satisfaction source)
     :no-demand [:p.covered "No demanding " demand-unit " at this source."]
     :covered   [:p.covered "All service demand covered."]
     [:p.unsatisfied
      (utils/format-number (or quantity 0))
      " " demand-unit " at this source are not covered."])])

(defn- source-icon-function
  [source]
  (let [classname (case (source-satisfaction source)
                    :covered "satisfied"
                    :q1      "q1"
                    :q2      "q2"
                    :q3      "q3"
                    :q4      "q4"
                    "gray")]
    {:className (str "marker-source-icon " classname)}))

(defn- scenario-sources-layer
  [{:keys [project scenario]}]
  (let [demand-unit  (get-demand-unit project)
        sources-data (:sources-data scenario)]
    (into [:feature-group {:key "sources-layer"}]
          (map (fn [{:keys [id lat lon name] :as source}]
                 [:marker {:key          id
                           :lat          lat
                           :lon          lon
                           :icon         (source-icon-function source)
                           :tooltip      (source-tooltip {:demand-unit demand-unit} source)
                           :source       source
                           :zIndexOffset 0}])
               sources-data))))


;;; Raster source layer

(defn- scenario-demand-layer
  [{:keys [raster] :as scenario}]
  (when raster
    [:wms-tile-layer
     {:keys        "demand-layer"
      :url         config/mapserver-url
      :transparent true
      :layers      "scenario"
      :DATAFILE    (str raster ".map")
      :format      "image/png"
      :opacity     0.6}]))


;;; Screen components

(def ^:private fit-options #js {:maxZoom            12
                                :paddingTopLeft     (js/L.Point. 400 50)
                                :paddingBottomRight (js/L.Point. 50 10)})

(defn simple-map
  [project _ _ _]
  (let [map-ref                 (atom nil)
        last-bbox               (atom nil)
        view-state              (subscribe [:scenarios/view-state])
        searching?              (subscribe [:scenarios/searching-providers?])
        matches-bbox            (subscribe [:scenarios.map/search-matches-bbox])
        initial-scenario?       (subscribe [:scenarios/initial-scenario?])
        position                (r/atom mapping/map-preview-position)
        zoom                    (r/atom 3)
        demand-unit             (get-demand-unit project)
        provider-unit           (get-provider-unit project)
        capacity-unit           (get-capacity-unit project)
        add-point               (fn [lat lon] (dispatch [:scenarios/create-provider {:lat lat :lon lon}]))
        provider-click-fn       (fn [{:keys [provider]}]
                                  (when-not @initial-scenario?
                                    (if (provider-has-change? provider)
                                      (dispatch [:scenarios/edit-change-in-dialog provider])
                                      (dispatch [:scenarios/create-change-in-dialog provider]))))
        provider-mouseover-fn   (fn [{:keys [provider]}] (dispatch [:scenarios.map/select-provider provider]))
        provider-mouseout-fn    (fn [{:keys [provider]}] (dispatch [:scenarios.map/unselect-provider provider]))
        suggestion-popup-fn     (fn [{:keys [suggestion]}]
                                  (show-suggested-provider {:demand-unit   demand-unit
                                                            :capacity-unit capacity-unit}
                                                           suggestion
                                                           @view-state))
        suggestion-mouseover-fn (fn [{:keys [suggestion]}] (dispatch [:scenarios.map/select-suggestion suggestion]))
        suggestion-mouseout-fn  (fn [{:keys [suggestion]}] (dispatch [:scenarios.map/unselect-suggestion suggestion]))]
    (fn [{:keys [bbox] :as project} scenario state error]
      ;; fit-bounds to search result matches
      (when (and @searching? @matches-bbox (not= @last-bbox @matches-bbox))
        (reset! last-bbox @matches-bbox)
        (let [[[s w] [n e]] @matches-bbox
              match-bbox    (js/L.latLngBounds (js/L.latLng s w) (js/L.latLng n e))]
          (r/after-render #(when @map-ref (.fitBounds @map-ref match-bbox fit-options)))))

      [:div.map-container (when error {:class "gray-filter"})
       [l/map-widget {:zoom                @zoom
                      :position            @position
                      :ref                 #(reset! map-ref %)
                      :on-position-changed #(reset! position %)
                      :on-zoom-changed     #(reset! zoom %)
                      :on-click            (cond (= state :new-provider) add-point)
                      :controls            [:attribution
                                            [:reference-table {:hide-actions? @initial-scenario?}]
                                            :mapbox-logo]
                      :initial-bbox        bbox
                      :pointer-class       (cond (= state :new-provider) "crosshair-pointer")}
        mapping/base-tile-layer
        (scenario-demand-layer scenario)
        mapping/labels-tile-layer
        (scenario-sources-layer {:project  project
                                 :scenario scenario})
        (scenario-providers-layer {:project      project
                                   :scenario     scenario
                                   :click-fn     provider-click-fn
                                   :mouseover-fn provider-mouseover-fn
                                   :mouseout-fn  provider-mouseout-fn})
        (scenario-selected-coverage-layer)
        (scenario-suggestions-layer {:popup-fn     suggestion-popup-fn
                                     :mouseover-fn suggestion-mouseover-fn
                                     :mouseout-fn  suggestion-mouseout-fn})]])))

(defn scenario-pending?
  [scenario]
  (= (:state scenario) "pending"))

(defn- format-percentage
  [num denom]
  (utils/format-percentage (/ num denom) 2))

(defn initial-scenario-panel
  [{:keys [name demand-coverage] :as scenario}]
  (let [source-demand   (subscribe [:scenarios.current/source-demand])
        current-project (subscribe [:projects2/current-project])
        demand-unit     (get-demand-unit @current-project)
        pending?        (scenario-pending? scenario)]
    [:<>
     [:div.section
      [:h1.large
       [:small (str "Initial " demand-unit " coverage")]
       (cond
         pending? "loading..."
         :else    (str (utils/format-number demand-coverage)
                       " (" (format-percentage demand-coverage @source-demand) ")"))]
      [:p.grey-text
       (str "of a total of " (utils/format-number @source-demand))]]
     [:div.section.expand-center
      [:p
       "This is the initial project scenario and cannot be modified. "
       "To make changes, create a new one from here."]
      [:div.actions
       [m/Button {:disabled pending?
                  :raised   true
                  :on-click #(dispatch [:scenarios/copy-scenario (:id scenario)])}
        "Create scenario"]]]]))

(defn scenario-info
  [view-state current-scenario]
  (let [{:keys [name label effort demand-coverage source-demand population-under-coverage increase-coverage state]} current-scenario
        current-project (subscribe [:projects2/current-project])
        analysis-type   (get-in @current-project [:config :analysis-type])
        demand-unit     (get-demand-unit @current-project)
        pending?        (scenario-pending? current-scenario)
        coverage-text       (if pending?
                              "loading..."
                              (str (utils/format-number increase-coverage) " (" (format-percentage increase-coverage source-demand) ")"))
        total-coverage-text (if pending?
                              "to a total of"
                              (str "to a total of " (utils/format-number demand-coverage) " (" (format-percentage demand-coverage source-demand) ")"))
        population-text     (if pending?
                              "loading..."
                              (utils/format-number population-under-coverage))]
    [:div
     [:div.section
      [:h1.large
       [:small (str "Increase in " demand-unit " coverage")]
       coverage-text]
      [:p.grey-text total-coverage-text]]
     [:div.section
      [:h1.large
       [:small (str "Total " demand-unit " under geographic coverage")]
       population-text]]
     [:div.section
      [:h1.large
       [:small "Effort required"]
       (utils/format-effort effort analysis-type)]]]))


(defn new-provider-unit?
  [view-state]
  (or (= view-state :new-provider)
      (= view-state :new-intervention)))

(defn suggestions-view
  [{:keys [project suggestions suggestion-type]}]
  (let [demand-unit   (get-demand-unit project)
        capacity-unit (get-capacity-unit project)
        provider-unit (get-provider-unit project)
        title         (case suggestion-type
                        :new-provider (str "Locations for new " provider-unit)
                        :improvement  (str (capitalize provider-unit) " for improvement")
                        "Suggestions")]
    [:<>
     [:div.section.sidebar-title
      [:h1 title]
      [ui/close-button {:on-click #(dispatch [:scenarios/close-suggestions])}]]
     [:div.scroll-list
      [changeset/suggestion-listing-component
       {:demand-unit   demand-unit
        :capacity-unit capacity-unit}
       suggestions]]]))

(defn scenario-view
  [{:keys [view-state scenario providers project error]}]
  (let [provider-unit (get-provider-unit project)
        demand-unit   (get-demand-unit project)
        capacity-unit (get-capacity-unit project)
        state-message (case view-state
                        :get-suggestions-for-new-provider "Computing best locations..."
                        :get-suggestions-for-improvements "Computing best improvements..."
                        :new-provider                     (str "Click on the map to add " provider-unit)
                        nil)]
    [:<>
     [:div
      [scenario-info view-state scenario]
      [edit/create-new-action-component {:provider-unit provider-unit
                                         :disabled      (scenario-pending? scenario)}]
      (when state-message
        [:div.info-computing-best-location
         [:small state-message]])]
     (if error
       [raise-alert scenario error]
       [:<>
        [changeset/listing-component {:demand-unit   demand-unit
                                      :capacity-unit capacity-unit}
         providers]
        [:div.search-bottom-bar
         {:on-click #(dispatch [:scenarios/start-searching])}
         [m/Icon "search"]
         [:span "Search facilities"]]])]))


(defn- search-view
  [project]
  (let [search-value    (r/atom "")
        close-fn        #(dispatch [:scenarios/cancel-search])
        dispatch-search (utils/debounced #(dispatch [:scenarios/search-providers %1 %2]) 500)
        update-search   (fn [value]
                          (reset! search-value value)
                          (dispatch-search value :forward))
        search-again    (fn [direction]
                          (dispatch-search :immediate @search-value direction))
        matches         (subscribe [:scenarios/search-providers-matches])
        demand-unit     (get-demand-unit project)
        capacity-unit   (get-capacity-unit project)]
    (fn [_]
      [:<>
       [:div.section.sidebar-title.search
        [m/Icon "search"]
        [:input {:type        :text
                 :auto-focus  true
                 :on-key-down (fn [evt]
                                (case (.-which evt)
                                  27      (close-fn)
                                  38      (search-again :backward)
                                  (13 40) (search-again :forward)
                                  nil))
                 :placeholder "Search facilities"
                 :value       @search-value
                 :on-change   #(update-search (-> % .-target .-value))}]
        [ui/close-button {:on-click close-fn}]]
       (when (seq @matches)
         [changeset/listing-component {:demand-unit   demand-unit
                                       :capacity-unit capacity-unit}
          @matches])])))

(defn side-panel-view-2
  [current-scenario error]
  (let [view-state               (subscribe [:scenarios/view-state])
        suggestions              (subscribe [:scenarios/suggestions])
        searching?               (subscribe [:scenarios/searching-providers?])
        providers-from-changeset (subscribe [:scenarios/providers-from-changeset])
        current-project          (subscribe [:projects2/current-project])]
    (cond
      @searching?
      [search-view @current-project]

      @suggestions
      [suggestions-view {:suggestions     @suggestions
                         :suggestion-type (case @view-state
                                            :new-provider     :new-provider
                                            :new-intervention :improvement
                                            nil)
                         :project         @current-project}]

      :else
      [scenario-view {:view-state            @view-state
                      :scenario              current-scenario
                      :providers             @providers-from-changeset
                      :project               @current-project
                      :error                 error}])))

(defn side-panel-view
  [scenario error]
  (if (db/initial-scenario? scenario)
    [initial-scenario-panel scenario]
    [side-panel-view-2 scenario error]))

(defn scenario-line-info
  [{:keys [effort source-demand increase-coverage] :as current-scenario}]
  (let [current-project (subscribe [:projects2/current-project])
        analysis-type   (get-in @current-project [:config :analysis-type])
        demand-unit     (get-demand-unit @current-project)]
    [:div.actions-table-header
     [:div
      [:h3.grey-text (str "Increase in " demand-unit " coverage "
                          (utils/format-number increase-coverage)
                          " (" (format-percentage increase-coverage source-demand) ")")]
      [:h3.grey-text (str "Effort required " (utils/format-effort effort analysis-type))]]]))

(defn actions-table-view
  [current-scenario]
  (let [current-project          (subscribe [:projects2/current-project])
        providers-from-changeset (subscribe [:scenarios/providers-from-changeset])
        demand-unit              (get-demand-unit @current-project)
        capacity-unit            (get-capacity-unit @current-project)]
    [:<>
     [scenario-line-info current-scenario]
     [changeset/table-component {:demand-unit demand-unit
                                 :capacity-unit capacity-unit
                                 :source-demand (:source-demand current-scenario)}
      @providers-from-changeset]]))

(defn sidebar-expand-button
  [expanded?]
  (let [[icon event] (if expanded?
                       ["arrow_left" :scenarios/collapse-sidebar]
                       ["arrow_right" :scenarios/expand-sidebar])]
    [:div.sidebar-expand-button
     [:div.sidebar-button {:on-click #(dispatch [event])}
      [m/Icon icon]]]))



(defn- scenario-actions
  [{:keys [source-type]} {:keys [id] :as scenario}]
  [[ui/menu-item {:disabled (scenario-pending? scenario)
                  :on-click #(dispatch [:scenarios/copy-scenario (:id scenario)])
                  :icon     "add"}
    "Create new scenario from here"]
   [ui/menu-item {:on-click #(dispatch [:scenarios/open-rename-dialog])
                  :icon     "edit"}
    "Rename scenario"]
   [ui/menu-item {:on-click #(dispatch [:scenarios/open-delete-dialog])
                  :icon     "delete"}
    "Delete scenario"]
   [m/ListDivider]
   [ui/link-menu-item {:url  (api/download-scenario-sources id)
                       :icon "download"}
    "Download sources "
    (case source-type
      "raster" "(TIF)"
      "(CSV)")]
   [ui/link-menu-item {:url (api/download-scenario-providers id)
                       :icon "download"}
    "Download providers (CSV)"]])

(defn- scenario-breadcrumb
  [current-project current-scenario]
  (when current-project
    [:ul {:class-name "breadcrumb-menu"}
     [:li [:a {:href (routes/projects2-show {:id (:id current-project)})}
           (:name current-project)]]
     [:li [m/Icon {:strategy "ligature" :use "keyboard_arrow_right"}]]
     [:li (if current-scenario
            (:name current-scenario)
            "...")]]))

(defn display-current-scenario
  [current-project current-scenario]
  (let [expanded-sidebar?   (subscribe [:scenarios/sidebar-expanded?])
        can-expand-sidebar? (subscribe [:scenarios/can-expand-sidebar?])
        initial-scenario?   (subscribe [:scenarios/initial-scenario?])
        state               (subscribe [:scenarios/view-state])
        error               (subscribe [:scenarios/error])]
    (fn [current-project current-scenario]
      [ui/full-screen
       (merge (common2/nav-params)
              {:main          [simple-map current-project current-scenario @state @error]
               :title         [scenario-breadcrumb current-project current-scenario]
               :title-actions (when-not @initial-scenario? (scenario-actions current-project current-scenario))
               :sidebar-prop  {:class [(if @expanded-sidebar? :expanded-sidebar :compact-sidebar)]}})
       (if @expanded-sidebar?
         [actions-table-view current-scenario]
         [side-panel-view current-scenario @error])
       [edit/rename-scenario-dialog]
       [edit/delete-scenario-dialog]
       [edit/changeset-dialog current-project current-scenario]
       (when @can-expand-sidebar?
         [sidebar-expand-button @expanded-sidebar?])])))

(defn scenarios-page
  []
  (let [current-scenario (subscribe [:scenarios/current-scenario])
        current-project  (subscribe [:projects2/current-project])]
    (fn []
      (if (and @current-scenario @current-project)
        [display-current-scenario @current-project @current-scenario]
        [ui/full-screen
         (merge (common2/nav-params)
                {:main         [:div.loading-wrapper
                                [:h3 "Loading..."]]
                 :title        [scenario-breadcrumb @current-project @current-scenario]
                 :sidebar-prop {:class "hidden"}})]))))
