(ns planwise.client.projects.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [planwise.client.mapping :refer [default-base-tile-layer
                                             gray-base-tile-layer
                                             static-image
                                             bbox-center]]
            [planwise.client.styles :as styles]
            [planwise.client.routes :as routes]
            [planwise.client.common :as common]
            [planwise.client.config :as config]
            [planwise.client.db :as db]
            [clojure.string :as str]
            [reagent.core :as r]
            [leaflet.core :refer [map-widget]]))

(defn new-project-button []
  [:button.primary
   {:on-click
    #(dispatch [:projects/begin-new-project])}
   "New Project"])

(defn search-box [projects-count show-new]
  (let [search-string (subscribe [:projects/search-string])]
    (fn [projects-count show-new]
      [:div.search-box
       [:div (common/pluralize projects-count "project")]
       [:input
        {:type "search"
         :placeholder "Search projects..."
         :value @search-string
         :on-change #(dispatch [:projects/search (-> % .-target .-value str)])}]
       (if show-new
         [new-project-button])])))

(defn no-projects-view []
  [:div.empty-list
   [:img {:src "/images/empty-projects.png"}]
   [:p "You have no projects yet"]
   [:div
    [new-project-button]]])


(defn new-project-dialog []
  (let [view-state (subscribe [:projects/view-state])
        regions (subscribe [:regions/list])
        new-project-goal (r/atom "")
        new-project-region-id (r/atom (:id (first @regions)))
        map-preview-zoom (r/atom 5)
        map-preview-position (r/atom (bbox-center (:bbox (first @regions))))]
    (fn []
      (let [selected-region-geojson (subscribe [:regions/geojson @new-project-region-id])
            cancel-fn #(dispatch [:projects/cancel-new-project])
            key-handler-fn #(case (.-which %)
                              27 (cancel-fn)
                              nil)]
        [:form.dialog.new-project {:on-key-down key-handler-fn
                                   :on-submit (fn []
                                                (dispatch [:projects/create-project {:goal @new-project-goal, :region_id @new-project-region-id}])
                                                (.preventDefault js/event))}
         [:div.title
          [:h1 "New Project"]
          [common/close-button {:on-click cancel-fn}]]
         [:div.form-control
          [:label "Goal"]
          [:input {:type "text"
                   :required true
                   :autoFocus true
                   :value @new-project-goal
                   :placeholder "Describe your project's goal"
                   :on-key-down key-handler-fn
                   :on-change #(reset! new-project-goal (-> % .-target .-value str str/triml))}]]
         [:div.form-control
          [:label "Location"]
          [rc/single-dropdown
            :choices @regions
            :label-fn :name
            :filter-box? true
            :on-change #(do (dispatch [:regions/load-regions-with-geo [%]]) (reset! new-project-region-id %))
            :model new-project-region-id]
          [map-widget { :position @map-preview-position
                        :zoom @map-preview-zoom
                        :on-position-changed #(reset! map-preview-position %)
                        :on-zoom-changed #(reset! map-preview-zoom %)
                        :width 500
                        :height 300
                        :controls []}
           default-base-tile-layer
           (if @selected-region-geojson
             [:geojson-layer {:data @selected-region-geojson
                              :fit-bounds true
                              :color styles/orange
                              :opacity 0.7
                              :fillOpacity 0.3
                              :weight 4}])]]
         [:div.actions
          [:button.primary
           {:type "submit"
            :disabled (= @view-state :creating)}
           (if (= @view-state :creating)
             "Creating..."
             "Create")]
          [:button.cancel
           {:type "button"
            :on-click cancel-fn}
           "Cancel"]]]))))

(defn project-stat [title stat]
  [:div.stat
   [:div.stat-title title]
   [:div.stat-value stat]])

(defn project-card [{:keys [id goal region_id facilities_count] :as project}]
  (let [region-geo (subscribe [:regions/geojson region_id])]
    (fn [{:keys [id goal region_id] :as project}]
      [:a {::href (routes/project-demographics project)}
        [:div.project-card
          [:div.project-card-content
           [:span.project-goal goal]
           [:div.project-stats
            (project-stat "TARGET FACILITIES" facilities_count)]]
          (if-not (str/blank? @region-geo)
            [:img.map-preview {:src (static-image @region-geo)}])]])))

(defn projects-list [projects]
  [:ul.projects-list
    (for [project projects]
      [:li {:key (:id project)}
        [project-card project]])])

(defn list-view []
  (let [view-state (subscribe [:projects/view-state])
        projects (subscribe [:projects/list])
        filtered-projects (subscribe [:projects/filtered-list])]
    (fn []
      [:article.project-list
       [search-box (count @filtered-projects) (seq @projects)]
       (cond
         (= @view-state :loading) [:div "Loading"]
         (empty? @projects) [no-projects-view]
         :else [projects-list @filtered-projects])
       (when (or (= @view-state :creating) (= @view-state :create-dialog))
         [common/modal-dialog {:on-backdrop-click
                               #(dispatch [:projects/cancel-new-project])}
          [new-project-dialog]])])))

(defn project-tab-items [project-id]
  (let [route-params {:id project-id}]
    [{:item :demographics
      :href (routes/project-demographics route-params)
      :title "Demographics"}
     {:item :facilities
      :href (routes/project-facilities route-params)
      :title "Facilities"}
     {:item :transport
      :href (routes/project-transport route-params)
      :title "Transport Means"}
     #_{:item :scenarios
        :href (routes/project-scenarios route-params)
        :title "Scenarios"}]))

(defn header-section [project-id project-goal selected-tab]
  [:div.project-header
   [:h2 project-goal]
   [:nav
    [common/ul-menu (project-tab-items project-id) selected-tab]
    #_[:a "Download Project"]]])

(defn transport-filters []
  (let [transport-time (subscribe [:projects/transport-time])]
    (fn []
      [:div.sidebar-filters
       [:div.filter-info
        [:p "Indicate here the acceptable travel times to facilities. We will
        use that to calculate who already has access to the services that you
        are analyzing."]]

       [:fieldset
        [:legend "By car"]
        [rc/single-dropdown
         :choices (:time db/transport-definitions)
         :label-fn :name
         :on-change #(dispatch [:projects/set-transport-time %])
         :model transport-time]]])))

(defn facility-filters []
  (let [facility-types (subscribe [:filter-definition :facility-type])
        facility-ownerships (subscribe [:filter-definition :facility-ownership])
        facility-services (subscribe [:filter-definition :facility-service])
        filters (subscribe [:projects/facilities :filters])
        filter-stats (subscribe [:projects/facilities :filter-stats])]
    (fn []
      (let [filter-count (:count @filter-stats)
            filter-total (:total @filter-stats)
            toggle-cons-fn (fn [field]
                             #(dispatch [:projects/toggle-filter :facilities field %]))]
        [:div.sidebar-filters
         [:div.filter-info
          [:p "Select the facilities that are satisfying the demand you are analyzing."]
          [:p
           [:div.small "Target / Total Facilities"]
           [:div (str filter-count " / " filter-total)]
           [common/progress-bar filter-count filter-total]]]

         [:fieldset
          [:legend "Type"]
          (common/filter-checkboxes
           {:options @facility-types
            :value (:type @filters)
            :toggle-fn (toggle-cons-fn :type)})]

         #_[:fieldset
            [:legend "Ownership"]
            (common/filter-checkboxes
             {:options @facility-ownerships
              :value (:ownership @filters)
              :toggle-fn (toggle-cons-fn :ownership)})]

         #_[:fieldset
            [:legend "Services"]
            (common/filter-checkboxes
             {:options @facility-services
              :value (:services @filters)
              :toggle-fn (toggle-cons-fn :services)})]]))))

(defn demographics-filters []
  [:div.sidebar-filters
   [:div.filter-info
    [:p "Filter here the population you are analyzing."]]])

(defn sidebar-section [selected-tab]
  [:aside (condp = selected-tab
           :demographics
           [demographics-filters]
           :facilities
           [facility-filters]
           :transport
           [transport-filters])])

(defn project-tab [project-id selected-tab]
  (let [facilities (subscribe [:projects/facilities :facilities])
        isochrones (subscribe [:projects/facilities :isochrones])
        map-position (subscribe [:projects/map-view :position])
        map-zoom (subscribe [:projects/map-view :zoom])
        map-bbox (subscribe [:projects/map-view :bbox])
        map-geojson (subscribe [:projects/map-geojson])]
    (fn [project-id selected-tab]
      (let [points (map (fn [fac] [(fac :lat) (fac :lon)]) @facilities)]
        (cond
          (#{:demographics
             :facilities
             :transport}
           selected-tab)
          [:div
           [sidebar-section selected-tab]
           [:div.map-container
            [map-widget {:position @map-position
                         :zoom @map-zoom
                         :min-zoom 5
                         :on-position-changed
                         #(dispatch [:projects/update-position %])
                         :on-zoom-changed
                         #(dispatch [:projects/update-zoom %])}
             ; Base tile layer
             gray-base-tile-layer
             ; Markers with filtered facilities
             [:point-layer {:points points
                            :radius 4
                            :color styles/black
                            :opacity 0.8
                            :weight 1
                            :fillOpacity 0.4}]
             ; Demographics tile layer
             [:tile-layer {:url config/demo-tile-url
                           :opacity 0.3}]
             ; Boundaries of working region
             (if @map-geojson
               [:geojson-layer {:data @map-geojson
                                :color styles/green
                                :fit-bounds true
                                :fillOpacity 0.1
                                :weight 0}])
             ; Isochrone for selected transport
             (if (and (seq @isochrones) (= :transport selected-tab))
               [:geojson-layer {:data @isochrones
                                :fillOpacity 1
                                :weight 2
                                :color styles/orange
                                :group {:opacity 0.4}}])]]]
          (= :scenarios selected-tab)
          [:div
           [:h1 "Scenarios"]])))))

(defn project-view []
  (let [page-params (subscribe [:page-params])
        current-project (subscribe [:projects/current-data])]
    (fn []
      (let [project-id (:id @page-params)
            selected-tab (:section @page-params)
            project-goal (:goal @current-project)]
        [:article.project-view
         [header-section project-id project-goal selected-tab]
         [project-tab project-id selected-tab]]))))

(defn project-page []
  (let [view-state (subscribe [:projects/view-state])]
    (fn []
      (if (= @view-state :loading)
        [:div "Loading"]
        [project-view]))))
