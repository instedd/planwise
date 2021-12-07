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
            [planwise.client.scenarios.api :as api]
            [planwise.client.scenarios.edit :as edit]
            [planwise.client.scenarios.changeset :as changeset]
            [planwise.client.components.common :as common]
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

(defn- provider-has-change?
  [provider]
  (some? (:change provider)))

(defn- show-provider
  [{:keys [read-only? demand-unit capacity-unit]}
   {:keys [change matches-filters name capacity
           free-capacity required-capacity
           satisfied-demand unsatisfied-demand reachable-demand]
    :as   provider}]
  (let [format-number (fnil utils/format-number 0)
        change*       (if (some? change)
                        change
                        (db/new-action provider (if (not matches-filters) :upgrade :increase)))]
    (crate/html
     [:div.mdc-typography
      [:h3 name]
      [:table
       [:tr
        [:td "Capacity:"]
        [:td (str (utils/format-number capacity) " " capacity-unit)]]
       [:tr
        [:td "Unsatisfied demand:"]
        [:td (utils/format-number unsatisfied-demand)]]
       [:tr
        [:td "Required capacity:"]
        [:td (utils/format-number (Math/ceil required-capacity))]]
       (when (or matches-filters change)
         [:tr
          [:td "Satisfied demand:"]
          [:td (utils/format-number satisfied-demand)]])
       (when (or matches-filters change)
         [:tr
          [:td "Free capacity: "]
          [:td (utils/format-number (Math/floor free-capacity))]])
       [:tr
        [:td (str (capitalize demand-unit) " under coverage:")]
        [:td (utils/format-number (Math/ceil reachable-demand))]]]
      (when-not read-only?
        [:div.actions
         (popup-connected-button
          (cond
            (some? change)        "Edit"
            (not matches-filters) "Upgrade"
            :else                 "Increase")
          #(dispatch [:scenarios/open-changeset-dialog (assoc provider :change change*)]))])])))

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

(defn- provider-icon-function
  [{:keys [id change matches-filters required-capacity satisfied-demand unsatisfied-demand capacity free-capacity]
    :as   provider}
   selected-provider]
  {:className
   (join " " ["leaflet-circle-icon"
              (when (= id (:id selected-provider)) "selected")
              (when (and (not change) (not matches-filters)) "upgradeable")
              (get-marker-class-for-provider provider)
              (when (provider-has-change? provider)
                "leaflet-circle-for-change")
              (when (some? change)
                (str "leaflet-" (:action change)))])})

(defn- scenario-providers-layer
  [{:keys [popup-fn mouseover-fn mouseout-fn]}]
  (let [selected-provider @(subscribe [:scenarios.map/selected-provider])
        searching?        @(subscribe [:scenarios/searching-providers?])
        matching-ids      @(subscribe [:scenarios/search-matching-ids])
        all-providers     @(subscribe [:scenarios/all-providers])]
    (into [:feature-group {}]
          (map (fn [{:keys [id location name] :as provider}]
                 (let [selected?    (= id (:id selected-provider))
                       matching?    (or (not searching?) (contains? matching-ids id))
                       marker-props {:key      id
                                     :lat      (:lat location)
                                     :lon      (:lon location)
                                     :icon     (provider-icon-function provider selected-provider)
                                     :provider provider}]
                   (if matching?
                     [:marker (merge marker-props
                                     {:tooltip      name
                                      :open?        (when selected? (:open? selected-provider))
                                      :hover?       (when selected? (:hover? selected-provider))
                                      :popup-fn     popup-fn
                                      :mouseover-fn mouseover-fn
                                      :mouseout-fn  mouseout-fn})]
                     [:marker (assoc marker-props :opacity 0.2)])))
               all-providers))))

(defn- scenario-selected-provider-layer
  []
  (when-let [selected-provider @(subscribe [:scenarios.map/selected-provider])]
    [:geojson-layer {:data      (:coverage-geom selected-provider)
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
  [suggestion selected-suggestion suggestion-type]
  {:html (str "<span>" (:ranked suggestion) "</span>")
   :className
   (->> ["leaflet-suggestion-icon"
         (when (= suggestion selected-suggestion) "selected")
         (case suggestion-type
           :new-provider "leaflet-suggestion-new-provider"
           :improvement  "leaflet-suggestion-new-improvement"
           nil)]
        (filter some?)
        (join " "))})

(defn- scenario-suggestions-layer
  [{:keys [popup-fn mouseover-fn mouseout-fn]}]
  (let [suggested-locations @(subscribe [:scenarios.new-provider/suggested-locations])
        selected-suggestion @(subscribe [:scenarios.map/selected-suggestion])
        view-state          @(subscribe [:scenarios/view-state])
        suggestion-type     (if (= view-state :new-provider)
                              :new-provider
                              :improvement)]
    (into [:feature-group {}]
          (map (fn [{:keys [location name] :as suggestion}]
                 [:marker {:lat          (:lat location)
                           :lon          (:lon location)
                           :tooltip      name
                           :icon         (suggestion-icon-function suggestion selected-suggestion suggestion-type)
                           :open?        (= suggestion selected-suggestion)
                           :suggestion   suggestion
                           :popup-fn     popup-fn
                           :mouseover-fn mouseover-fn
                           :mouseout-fn  mouseout-fn}])
               suggested-locations))))


;;; Sources

(defn- show-source
  [{:keys [name initial-quantity quantity]}]
  (let [display-initial-quantity (.toFixed (or initial-quantity 0) 2)
        display-quantity         (.toFixed (or quantity 0) 2)]
    (crate/html
     [:div.mdc-typography
      [:h3 name]
      [:table
       [:tr
        [:td "Original quantity:"]
        [:td display-initial-quantity]]
       [:tr
        [:td "Current quantity:"]
        [:td display-quantity]]]])))

(defn- source-icon-function
  [source]
  (let [quantity-initial (:initial-quantity source)
        quantity-current (:quantity source)
        ratio            (if (pos? quantity-initial) (/ quantity-current quantity-initial) 0)
        classname        (cond
                           (= 0 quantity-initial) "leaflet-square-icon-gray"
                           (<= ratio 0.25)        "leaflet-square-icon-green"
                           (< 0.25 ratio 0.5)     "leaflet-sqaure-icon-yellow"
                           (<= 0.5 ratio 0.75)    "leaflet-square-icon-orange"
                           (> ratio 0.75)         "leaflet-square-icon-red")]
    {:className classname}))

(defn- scenario-sources-layer
  [{:keys [sources-data] :as scenario} {:keys [popup-fn]}]
  (into [:feature-group {}]
        (map (fn [{:keys [id lat lon name] :as source}]
               [:marker {:key      id
                         :lat      lat
                         :lon      lon
                         :icon     (source-icon-function source)
                         :tooltip  name
                         :source   source
                         :popup-fn popup-fn}])
             sources-data)))


;;; Raster source layer

(defn- scenario-demand-layer
  [{:keys [raster] :as scenario}]
  (when raster
    [:wms-tile-layer
     {:url         config/mapserver-url
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
  [project _ _ _ read-only?]
  (let [map-ref                 (atom nil)
        last-bbox               (atom nil)
        view-state              (subscribe [:scenarios/view-state])
        searching?              (subscribe [:scenarios/searching-providers?])
        matches-bbox            (subscribe [:scenarios.map/search-matches-bbox])
        position                (r/atom mapping/map-preview-position)
        zoom                    (r/atom 3)
        demand-unit             (get-demand-unit project)
        provider-unit           (get-provider-unit project)
        capacity-unit           (get-capacity-unit project)
        add-point               (fn [lat lon] (dispatch [:scenarios/create-provider {:lat lat :lon lon}]))
        source-popup-fn         (fn [{:keys [source]}] (show-source source))
        provider-popup-fn       (fn [{:keys [provider]}] (show-provider {:read-only?    read-only?
                                                                         :demand-unit   demand-unit
                                                                         :capacity-unit capacity-unit}
                                                                        provider))
        provider-mouseover-fn   (fn [{:keys [provider]}] (dispatch [:scenarios.map/select-provider provider]))
        provider-mouseout-fn    (fn [{:keys [provider]}] (dispatch [:scenarios.map/unselect-provider provider]))
        suggestion-popup-fn     (fn [{:keys [suggestion]}]
                                  (show-suggested-provider {:demand-unit   demand-unit
                                                            :capacity-unit capacity-unit}
                                                           suggestion
                                                           @view-state))
        suggestion-mouseover-fn (fn [{:keys [suggestion]}] (dispatch [:scenarios.map/select-suggestion suggestion]))
        suggestion-mouseout-fn  (fn [{:keys [suggestion]}] (dispatch [:scenarios.map/unselect-suggestion suggestion]))]
    (fn [{:keys [bbox] :as project} scenario state error read-only?]
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
                      :controls            [[:legend {:provider-unit provider-unit}]]
                      :initial-bbox        bbox
                      :pointer-class       (cond (= state :new-provider) "crosshair-pointer")}
        mapping/default-base-tile-layer
        (scenario-demand-layer scenario)
        (scenario-sources-layer scenario {:popup-fn source-popup-fn})
        (scenario-providers-layer {:popup-fn     provider-popup-fn
                                   :mouseover-fn provider-mouseover-fn
                                   :mouseout-fn  provider-mouseout-fn})
        (scenario-selected-provider-layer)
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
  [{:keys [name demand-coverage state]}]
  (let [source-demand   (subscribe [:scenarios.current/source-demand])
        current-project (subscribe [:projects2/current-project])
        demand-unit     (get-demand-unit @current-project)]
    [:div
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

(defn- close-button
  [{:keys [on-click]}]
  [:button.icon-button {:on-click on-click} [m/Icon "close"]])

(defn suggestions-view
  [{:keys [project suggestions suggestion-type]}]
  (let [demand-unit   (get-demand-unit project)
        capacity-unit (get-capacity-unit project)
        provider-unit (get-provider-unit project)
        title         (case suggestion-type
                        :new-provider (str "Locations for new " provider-unit)
                        :improvement  (str (capitalize provider-unit) " for capacity increase")
                        "Suggestions")]
    [:<>
     [:div.section.sidebar-title
      [:h1 title]
      [close-button {:on-click #(dispatch [:scenarios/close-suggestions])}]]
     [:div.scroll-list
      [changeset/suggestion-listing-component
       {:demand-unit   demand-unit
        :capacity-unit capacity-unit}
       suggestions]]]))

(defn scenario-view
  [{:keys [view-state scenario computing? computing-best-locations? providers project error]}]
  (let [provider-unit (get-provider-unit project)
        demand-unit   (get-demand-unit project)
        capacity-unit (get-capacity-unit project)]
    [:<>
     [:div
      [scenario-info view-state scenario]
      [edit/create-new-action-component {:provider-unit provider-unit
                                         :disabled      (scenario-pending? scenario)}]
      (if computing?
        [:div.info-computing-best-location
         [:small (if computing-best-locations?
                   "Computing best locations ..."
                   "Computing best improvements...")]])]
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
        [close-button {:on-click close-fn}]]
       (when (seq @matches)
         [changeset/listing-component {:demand-unit   demand-unit
                                       :capacity-unit capacity-unit}
          @matches])])))

(defn side-panel-view-2
  [current-scenario error]
  (let [computing-best-locations?    (subscribe [:scenarios.new-provider/computing-best-locations?])
        computing-best-improvements? (subscribe [:scenarios.new-intervention/computing-best-improvements?])
        suggested-locations          (subscribe [:scenarios.new-provider/suggested-locations])
        view-state                   (subscribe [:scenarios/view-state])
        searching?                   (subscribe [:scenarios/searching-providers?])
        providers-from-changeset     (subscribe [:scenarios/providers-from-changeset])
        current-project              (subscribe [:projects2/current-project])
        computing-suggestions?       (or @computing-best-locations? @computing-best-improvements?)]
    (cond
      @searching?
      [search-view @current-project]

      @suggested-locations
      [suggestions-view {:suggestions     @suggested-locations
                         :suggestion-type (case @view-state
                                            :new-provider     :new-provider
                                            :new-intervention :improvement
                                            nil)
                         :project         @current-project}]

      :else
      [scenario-view {:view-state                @view-state
                      :scenario                  current-scenario
                      :computing?                computing-suggestions?
                      :computing-best-locations? @computing-best-locations?
                      :providers                 @providers-from-changeset
                      :project                   @current-project
                      :error                     error}])))

(defn side-panel-view
  [current-scenario error read-only?]
  [:<>
   (if read-only?
     [initial-scenario-panel current-scenario]
     [side-panel-view-2 current-scenario error])])

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
        read-only?          (subscribe [:scenarios/read-only?])
        state               (subscribe [:scenarios/view-state])
        error               (subscribe [:scenarios/error])]
    (fn [current-project current-scenario]
      [ui/full-screen
       (merge (common2/nav-params)
              {:main          [simple-map current-project current-scenario @state @error @read-only?]
               :title         [scenario-breadcrumb current-project current-scenario]
               :title-actions (scenario-actions current-project current-scenario)
               :sidebar-prop  {:class [(if @expanded-sidebar? :expanded-sidebar :compact-sidebar)]}})
       (if @expanded-sidebar?
         [actions-table-view current-scenario]
         [side-panel-view current-scenario @error @read-only?])
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
