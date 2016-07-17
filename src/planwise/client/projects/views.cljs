(ns planwise.client.projects.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.mapping :refer [default-base-tile-layer]]
            [planwise.client.routes :as routes]
            [planwise.client.common :as common]
            [reagent.core :as r]
            [leaflet.core :refer [map-widget]]))

(defn search-box []
  [:div.search-box
   [:div "0 Projects"]
   [:input {:type "search"}]])

(defn no-projects-view []
  [:div.empty-list
   [:img {:src "/images/empty-projects.png"}]
   [:p "You have no projects yet"]
   [:div
    [:button.primary
     {:on-click
      #(dispatch [:projects/begin-new-project])}
     "New Project"]]])

(defn new-project-dialog []
  (let [new-project-goal (r/atom "")
        view-state (subscribe [:projects/view-state])]
    (fn []
      [:div.dialog
       [:div.title
        [:h1 "New Project"]
        [:button.close {:on-click
                        #(dispatch [:projects/cancel-new-project])}
         "X"]]
       [:div.form-control
        [:label "Goal"]
        [:input {:type "text"
                 :value @new-project-goal
                 :placeholder "Describe your project's goal"
                 :on-change #(reset! new-project-goal (-> % .-target .-value str))}]]
       [:div.form-control
        [:label "Location"]
        [:input {:type "search" :placeholder "Enter your project's location"}]]
       [map-widget {:width 400
                    :height 300
                    :position [0 0]
                    :zoom 1
                    :controls []}
        default-base-tile-layer]
       [:div.actions
        [:button.primary
         {:disabled (= @view-state :creating)
          :on-click #(dispatch [:projects/create-project {:goal @new-project-goal}])}
         (if (= @view-state :creating)
           "Creating..."
           "Create")]
        [:button.cancel
         {:on-click
          #(dispatch [:projects/cancel-new-project])}
         "Cancel"]]])))

(defn list-view []
  (let [view-state (subscribe [:projects/view-state])]
    (fn []
      [:article.project-list
       [search-box]
       [no-projects-view]
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

(defn project-tab [project-id selected-tab]
  (let [facilities (subscribe [:projects/facilities :facilities])
        map-view (subscribe [:projects/facilities :map-view])]
    (fn []
      (let [position (:position @map-view)
            zoom (:zoom @map-view)
            points (map (fn [fac] [(fac :lat) (fac :lon)]) @facilities)]
        (cond
          (#{:demographics
             :facilities
             :transport}
           selected-tab)
          [:div
           [sidebar-section selected-tab]
           [:div.map-container
            [map-widget {:position position
                         :zoom zoom
                         :on-position-changed
                         #(dispatch [:projects/update-position %])
                         :on-zoom-changed
                         #(dispatch [:projects/update-zoom %])}
             default-base-tile-layer
             [:point-layer {:points points
                            :radius 3
                            :color "#f00"
                            :opacity 0.3
                            :fillOpacity 0.3}]]]]
          (= :scenarios selected-tab)
          [:div
           [:h1 "Scenarios"]])))))

(defn project-view []
  (let [page-params (subscribe [:page-params])
        current-project (subscribe [:projects/current])]
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
