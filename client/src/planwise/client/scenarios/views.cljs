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

;; Minimum percentage of free capacity (over total capacity) for a provider to be considered with excess capacity
(def ^:private excess-threshold 10)

;; Max percentage of unsatisfied demand (over reachable demand) for a provider to be considered with satisfaction covered
(def ^:private cover-threshold  1)

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
  [{:keys [unsatisfied-demand reachable-demand capacity free-capacity] :as provider}]
  (cond
    (> (get-percentage capacity free-capacity) excess-threshold)                   :excess
    (and (>= free-capacity 0)
         (< (get-percentage reachable-demand unsatisfied-demand) cover-threshold)) :covered
    :else                                                                          :unsatisfied))

(defn- subsume-provider-satisfaction
  [a b]
  (cond
    (or (= :unsatisfied a) (= :unsatisfied b)) :unsatisfied
    (or (= :covered a) (= :covered b))         :covered
    :else                                      :excess))

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
        "Relocation might be needed."
        [:br]
        "There's an excess of "
        (utils/format-units (Math/floor free-capacity) capacity-unit) "."]

       (and has-change? covered?)
       [:p.covered
        (str "Demand covered (within " cover-threshold "% margin)")]

       has-change?
       [:p.unsatisfied
        "Requires increase of "
        (utils/format-units (Math/ceil required-capacity) capacity-unit)
        [:br]
        "to cover " (utils/format-units unsatisfied-demand demand-unit)
        " without service."]

       (and (not has-change?) has-excess?)
       [:p.excess
        "All demand covered"
        [:br]
        "with " (utils/format-number (Math/floor free-capacity))
        " idle extra " capacity-unit "."]

       (and (not has-change?) covered?)
       [:p.covered
        "All demand covered."]

       (and (not has-change?) needs-upgrade?)
       [:p.unsatisfied
        "Requires upgrade and increase of "
        [:br]
        (utils/format-units (Math/ceil required-capacity) capacity-unit)
        " to cover "
        (utils/format-units unsatisfied-demand demand-unit) "."]

       :else
       [:p.unsatisfied
        "Requires increase of "
        (utils/format-units (Math/ceil required-capacity) capacity-unit)
        " to cover "
        [:br]
        (utils/format-units unsatisfied-demand demand-unit)
        " without service."])]))

(defn- satisfaction->icon-class
  [satisfaction]
  (case satisfaction
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
                      (satisfaction->icon-class (provider-satisfaction provider))]]
    (if has-change?
      {:html (str "<i class='material-icons'>" (get changeset/action-icons (:action change)) "</i>")
       :className
       (join " " (conj base-classes "for-change"))}
      {:className
       (join " " (conj base-classes (when (not matches-filters) "upgradeable")))})))

(defn- provider-cluster-icon-fn
  [cluster]
  (let [markers (.getAllChildMarkers cluster)
        length  (count markers)]
    (let [providers     (map #(js->clj (aget (aget % "options") "provider") :keywordize-keys true) markers)
          cluster-class (->> providers
                             (map provider-satisfaction)
                             (reduce subsume-provider-satisfaction)
                             satisfaction->icon-class)]
      {:html      (str "<b>" (if (> length 9) "9+" length) "</b>")
       :className (str "cluster-provider-icon " cluster-class)})))

(defn- scenario-providers-layer
  [{:keys [project scenario click-fn popup-fn mouseover-fn mouseout-fn]}]
  (let [selected-provider    @(subscribe [:scenarios.map/selected-provider])
        listing-suggestions? @(subscribe [:scenarios/listing-suggestions?])
        searching?           @(subscribe [:scenarios/searching-providers?])
        matching-ids         @(subscribe [:scenarios/search-matching-ids])
        all-providers        @(subscribe [:scenarios/all-providers])
        demand-unit          (get-demand-unit project)
        capacity-unit        (get-capacity-unit project)]
    ;; TODO: maybe change for :cluster-group
    (into [:feature-group {:key             "providers-layer"
                           :cluster-icon-fn provider-cluster-icon-fn}]
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
                                   (if disabled?
                                     {:opacity 0.3}
                                     {:tooltip      (provider-tooltip {:demand-unit   demand-unit
                                                                       :capacity-unit capacity-unit}
                                                                      provider)
                                      :opacity      1.0
                                      :hover?       selected?
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

(defn- suggestion-tooltip
  [{:keys [demand-unit capacity-unit]}
   {:keys [id action-capacity action-cost coverage name change ranked] :as suggestion}]
  (let [create?         (or (nil? id) (nil? change))
        increase?       (= "increase-provider" (:action change))
        upgrade?        (= "upgrade-provider" (:action change))
        action-capacity (Math/ceil action-capacity)]
    [:div.mdc-typography
     [:h3 (if name name (str "Suggestion " ranked))]
     (cond
       create?
       [:p
        "Build new provider with " (utils/format-units action-capacity capacity-unit)
        " to provide service to " (utils/format-units coverage demand-unit) "."]

       increase?
       [:p
        "Add " (utils/format-units action-capacity capacity-unit)
        (if action-cost
          (str " for an investment of " (utils/format-currency action-cost) ".")
          ".")]

       upgrade?
       [:p
        "Upgrade this provider and add " (utils/format-units action-capacity capacity-unit)
        (if action-cost
          (str " for an investment of " (utils/format-currency action-cost) ".")
          ".")])]))

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
  [{:keys [project click-fn mouseover-fn mouseout-fn]}]
  (let [suggestions         @(subscribe [:scenarios/suggestions])
        selected-suggestion @(subscribe [:scenarios.map/selected-suggestion])
        capacity-unit       (get-capacity-unit project)
        demand-unit         (get-demand-unit project)]
    (into [:feature-group {:key "suggestions-layer"}]
          (map (fn [{:keys [location name] :as suggestion}]
                 [:marker {:lat          (:lat location)
                           :lon          (:lon location)
                           :icon         (suggestion-icon-function suggestion selected-suggestion)
                           :tooltip      (suggestion-tooltip {:capacity-unit capacity-unit
                                                              :demand-unit   demand-unit}
                                                             suggestion)
                           :hover?       (= suggestion selected-suggestion)
                           :suggestion   suggestion
                           :click-fn     click-fn
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

(defn- subsume-source-satisfaction
  [a b]
  (cond
    (or (= :q4 a) (= :q4 b))           :q4
    (or (= :q3 a) (= :q3 b))           :q3
    (or (= :q2 a) (= :q2 b))           :q2
    (or (= :q1 a) (= :q1 b))           :q1
    (or (= :covered a) (= :covered b)) :covered
    :else                              :no-demand))

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

(defn- source-satisfaction->icon-class
  [satisfaction]
  (case satisfaction
    :covered "satisfied"
    :q1      "q1"
    :q2      "q2"
    :q3      "q3"
    :q4      "q4"
    "gray"))

(defn- source-icon-function
  [source disabled?]
  (let [classname (source-satisfaction->icon-class (source-satisfaction source))]
    {:className (join " " ["marker-source-icon" classname (when disabled? "disabled")])}))

(defn- source-cluster-icon-fn
  [cluster]
  (let [markers (.getAllChildMarkers cluster)
        length  (count markers)]
    (let [sources       (map #(js->clj (aget (aget % "options") "source") :keywordize-keys true) markers)
          cluster-class (->> sources
                             (map source-satisfaction)
                             (reduce subsume-source-satisfaction)
                             source-satisfaction->icon-class)]
      {:html      (str "<b>" (if (> length 9) "9+" length) "</b>")
       :className (str "cluster-source-icon " cluster-class)})))

(defn- scenario-sources-layer
  [{:keys [project scenario]}]
  (let [listing-suggestions? @(subscribe [:scenarios/listing-suggestions?])
        disabled?            listing-suggestions?
        demand-unit          (get-demand-unit project)
        sources-data         (:sources-data scenario)]
    ;; TODO: maybe change to :cluster-group if we ever improve it
    (into [:feature-group {:key             "sources-layer"
                           :cluster-icon-fn source-cluster-icon-fn}]
          (map (fn [{:keys [id lat lon name] :as source}]
                 [:marker (merge {:key          id
                                  :lat          lat
                                  :lon          lon
                                  :icon         (source-icon-function source disabled?)
                                  :source       source
                                  :zIndexOffset 0}
                                 (if disabled?
                                   {:opacity 0.3}
                                   {:opacity 1.0
                                    :tooltip (source-tooltip {:demand-unit demand-unit} source)}))])
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

;; NB.: these values are arbitrarily hand-picked in an attempt to ensure that
;; the markers and their tooltips are visible when panning/bounds fitting

(def ^:private fit-options #js {:maxZoom            11
                                :paddingTopLeft     (js/L.Point. 400 50)
                                :paddingBottomRight (js/L.Point. 100 10)})
(def ^:private pan-options #js {:paddingTopLeft     (js/L.Point. 600 150)
                                :paddingBottomRight (js/L.Point. 200 50)})

(defn- bbox-fitter
  [props]
  (let [last-bbox   (atom nil)
        wanted-bbox (subscribe [:scenarios.map/wanted-bbox])]
    (fn [{:keys [map-ref]}]
      (when (and @wanted-bbox (not= @last-bbox @wanted-bbox))
        (reset! last-bbox @wanted-bbox)
        (let [[[s w] [n e]] @wanted-bbox
              match-bbox    (js/L.latLngBounds (js/L.latLng s w) (js/L.latLng n e))]
          (r/after-render #(when @map-ref (.fitBounds @map-ref match-bbox fit-options)))))
      nil)))

(defn- auto-panner
  [props]
  (let [last-location    (atom nil)
        focused-location (subscribe [:scenarios.map/focused-location])]
    (fn [{:keys [map-ref]}]
      (when (not= @last-location @focused-location)
        (reset! last-location @focused-location)
        (when @focused-location
          (let [{:keys [lat lon]} @focused-location
                location          (js/L.latLng lat lon)]
            (r/after-render #(when @map-ref (.panInside @map-ref location pan-options))))))
      nil)))

(defn simple-map
  [project _ _ _]
  (let [map-ref                 (atom nil)
        set-ref-fn              #(reset! map-ref %)
        view-state              (subscribe [:scenarios/view-state])
        initial-scenario?       (subscribe [:scenarios/initial-scenario?])
        position                (r/atom mapping/map-preview-position)
        zoom                    (r/atom 3)
        set-position-fn         #(reset! position %)
        set-zoom-fn             #(reset! zoom %)
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
        suggestion-click-fn     (fn [{:keys [suggestion]}] (dispatch [:scenarios/edit-suggestion suggestion]))
        suggestion-mouseover-fn (fn [{:keys [suggestion]}] (dispatch [:scenarios.map/select-suggestion suggestion]))
        suggestion-mouseout-fn  (fn [{:keys [suggestion]}] (dispatch [:scenarios.map/unselect-suggestion suggestion]))]
    (fn [{:keys [bbox] :as project} scenario state error]
      [:div.map-container (when error {:class "gray-filter"})
       [bbox-fitter {:map-ref map-ref}]
       [auto-panner {:map-ref map-ref}]
       [l/map-widget {:zoom                @zoom
                      :position            @position
                      :ref                 set-ref-fn
                      :on-position-changed set-position-fn
                      :on-zoom-changed     set-zoom-fn
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
        (scenario-suggestions-layer {:project      project
                                     :click-fn     suggestion-click-fn
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
