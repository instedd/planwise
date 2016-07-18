(ns planwise.client.projects.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [planwise.client.mapping :refer [default-base-tile-layer static-image bbox-center]]
            [planwise.client.routes :as routes]
            [planwise.client.common :as common]
            [clojure.string :as str]
            [reagent.core :as r]
            [leaflet.core :refer [map-widget]]))

(defn new-project-button []
  [:button.primary
   {:on-click
    #(dispatch [:projects/begin-new-project])}
   "New Project"])

(defn search-box [projects-count show-new]
  [:div.search-box
   [:div (str projects-count " projects")]
   [:input
    {:type "search"
     :on-change #(dispatch [:projects/search (-> % .-target .-value str)])}]
   (if show-new
    [new-project-button])])

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
      (let [selected-region-geojson (subscribe [:regions/geojson @new-project-region-id])]
        [:form.dialog.new-project {:on-submit (fn []
                                                (dispatch [:projects/create-project {:goal @new-project-goal, :region_id @new-project-region-id}])
                                                (.preventDefault js/event))}
         [:div.title
          [:h1 "New Project"]
          [:button.close {:on-click
                          #(dispatch [:projects/cancel-new-project])}
           "\u2716"]]
         [:div.form-control
          [:label "Goal"]
          [:input {:type "text"
                   :required true
                   :value @new-project-goal
                   :placeholder "Describe your project's goal"
                   :on-change #(reset! new-project-goal (-> % .-target .-value str))}]]
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
                        :min-zoom 3
                        :width 400
                        :height 300
                        :controls []}
           default-base-tile-layer
           (if @selected-region-geojson
             [:geojson-layer {:data @selected-region-geojson
                              :fit-bounds true
                              :color "#f00"
                              :opacity 0.2
                              :weight 2}])]]
         [:div.actions
          [:button.primary
           {:type "submit"
            :disabled (= @view-state :creating)}
           (if (= @view-state :creating)
             "Creating..."
             "Create")]
          [:button.cancel
           {:on-click
            #(dispatch [:projects/cancel-new-project])}
           "Cancel"]]]))))

(defn project-card [{:keys [id goal region_id] :as project}]
  (let [region-geo (subscribe [:regions/geojson region_id])]
    (fn []
      [:a {::href (routes/project-demographics project)}
        [:div.project-card
          [:div.project-card-content
            [:span.project-goal goal]]
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
     {:item :scenarios
      :href (routes/project-scenarios route-params)
      :title "Scenarios"}]))

(defn header-section [project-id project-goal selected-tab]
  [:div.project-header
   [:h2 project-goal]
   [:nav
    [common/ul-menu (project-tab-items project-id) selected-tab]
    #_[:a "Download Project"]]])

(defn progress-bar [value]
  (let [percent-width (-> value
                          (* 100)
                          (str "%"))]
    [:div.progress-bar
     [:div.progress-filled {:style {:width percent-width}}]]))

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
        [:div.facility-filters
         [:div.filter-info
          [:p "Select the facilities that are satisfying the demand you are analyzing"]
          [:p
           [:div.small "Target / Total Facilities"]
           [:div (str filter-count " / " filter-total)]
           [progress-bar (/ filter-count filter-total)]]]

         [:fieldset
          [:legend "Type"]
          (common/filter-checkboxes
           {:options @facility-types
            :value (:type @filters)
            :toggle-fn (toggle-cons-fn :type)})]

         [:fieldset
          [:legend "Ownership"]
          (common/filter-checkboxes
           {:options @facility-ownerships
            :value (:ownership @filters)
            :toggle-fn (toggle-cons-fn :ownership)})]

         [:fieldset
          [:legend "Services"]
          (common/filter-checkboxes
           {:options @facility-services
            :value (:services @filters)
            :toggle-fn (toggle-cons-fn :services)})]]))))

(defn sidebar-section [selected-tab]
  [:aside (condp = selected-tab
           :demographics
           [:h3 "Demographics filters"]
           :facilities
           [facility-filters]
           :transport
           [:h3 "Transport filters"])])

(defn project-tab [current-project selected-tab]
  (let [bbox (subscribe [:regions/bbox (:region_id current-project)])
        admin-level (subscribe [:regions/admin-level (:region_id current-project)])
        geojson (subscribe [:regions/geojson (:region_id current-project)])]
    (fn []
      (cond
        (#{:demographics
           :facilities
           :transport}
         selected-tab)
        [:div
         [sidebar-section selected-tab]
         [:div.map-container
          [map-widget {:position (bbox-center @bbox)
                       :max-bounds @bbox
                       :min-zoom 6
                       :zoom (+ 8 @admin-level)}
           default-base-tile-layer
           (if @geojson
             [:geojson-layer {:data @geojson
                              :fit-bounds true
                              :color "#0ff"
                              :fillOpacity 0.1
                              :weight 0}])]]]
        (= :scenarios selected-tab)
        [:div
         [:h1 "Scenarios"]]))))

(defn project-view []
  (let [page-params (subscribe [:page-params])
        current-project (subscribe [:projects/current])]
    (fn []
      (let [project-id (:id @page-params)
            selected-tab (:section @page-params)
            project-goal (:goal @current-project)]
        [:article.project-view
         [header-section project-id project-goal selected-tab]
         [project-tab @current-project selected-tab]]))))

(defn project-page []
  (let [view-state (subscribe [:projects/view-state])]
    (fn []
      (if (= @view-state :loading)
        [:div "Loading"]
        [project-view]))))
