(ns planwise.client.projects.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.mapping :refer [default-base-tile-layer]]
            [planwise.client.routes :as routes]
            [planwise.client.common :as common]
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
  [:div.dialog
   [:div.title
    [:h1 "New Project"]
    [:button.close {:on-click
                    #(dispatch [:projects/cancel-new-project])}
     "X"]]
   [:div.form-control
    [:label "Goal"]
    [:input {:type "text" :placeholder "Describe your project's goal"}]]
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
      #(dispatch [:projects/create-project])}
     "Continue"]
    [:button.cancel
     {:on-click
      #(dispatch [:projects/cancel-new-project])}
     "Cancel"]]])

(defn list-view []
  (let [creating-project? (subscribe [:projects/creating?])]
    (fn []
      [:div
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
   [common/ul-menu (project-tab-items project-id) selected-tab]
   [:a "Download Project"]])

(defn sidebar-section [selected-tab]
  (condp = selected-tab
    :demographics
    [:h3 "Demographics filters"]
    :facilities
    [:h3 "Facility filters"]
    :transport
    [:h3 "Transport filters"]))

(defn project-tab [project-id selected-tab]
  (cond
    (#{:demographics
       :facilities
       :transport}
     selected-tab)
    [:div
     [sidebar-section selected-tab]
     [:div
      [map-widget {:width 800
                   :height 800
                   :position [0 0]
                   :zoom 1}
       default-base-tile-layer]]]
    (= :scenarios selected-tab)
    [:div
     [:h1 "Scenarios"]]))

(defn project-view []
  (let [page-params (subscribe [:page-params])]
    (fn []
      (let [project-id (first @page-params)
            selected-tab (nth @page-params 1)]
        [:div
         [header-section project-id selected-tab]
         [project-tab project-id selected-tab]]))))
