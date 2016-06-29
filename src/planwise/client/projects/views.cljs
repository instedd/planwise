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
  (let [new-project-goal (r/atom "")]
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
         {:on-click
          #(dispatch [:projects/create-project {:goal @new-project-goal}])}
         "Continue"]
        [:button.cancel
         {:on-click
          #(dispatch [:projects/cancel-new-project])}
         "Cancel"]]])))

(defn list-view []
  (let [creating-project? (subscribe [:projects/creating?])]
    (fn []
      [:article.project-list
       [search-box]
       [no-projects-view]
       (when @creating-project?
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

(defn header-section [project-id selected-tab]
  [:div.project-header
   [:h2 "Test Project"]
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
  (cond
    (#{:demographics
       :facilities
       :transport}
     selected-tab)
    [:div
     [sidebar-section selected-tab]
     [:div.map-container
      [map-widget {:position [0 0]
                   :zoom 2}
       default-base-tile-layer]]]
    (= :scenarios selected-tab)
    [:div
     [:h1 "Scenarios"]]))

(defn project-view []
  (let [page-params (subscribe [:page-params])]
    (fn []
      (let [project-id (first @page-params)
            selected-tab (nth @page-params 1)]
        [:article.project-view
         [header-section project-id selected-tab]
         [project-tab project-id selected-tab]]))))
