(ns planwise.client.projects2.components.listings
  (:require [re-frame.core :refer [subscribe dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.components.common2 :as common2]
            [planwise.client.routes :as routes]
            [planwise.client.utils :as utils]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.mapping :refer [static-image fullmap-region-geo]]
            [planwise.client.components.common :as common]))

(def map-preview-size {:width 373 :height 278})

(defn- project-card
  [props project]
  (let [id              (:id project)
        region-id       (:region-id project)
        region-geo      (subscribe [:regions/preview-geojson region-id])
        preview-map-url (if region-id
                          (static-image @region-geo map-preview-size)
                          (static-image fullmap-region-geo map-preview-size))]
    (when region-id (dispatch [:regions/load-regions-with-preview [region-id]]))
    [ui/card {:href (routes/projects2-show {:id id})
              :primary [:img {:style map-preview-size :src preview-map-url}]
              :title (utils/or-blank (:name project) [:i "Untitled"])
              :status (utils/or-blank (:state project) [:i "status: unknown"])}]))

(defn- projects-list
  [projects]
  [ui/card-list {}
   (for [project projects]
     [project-card {:key (:id project)} project])])

(defn- no-projects-view
  []
  [:div.empty-list-container
   [:div.empty-list
    [:i.material-icons.md-icon-96 "folder"]
    [:p.message-margin "You have no projects yet"]
    [m/Button {:type       "button"
               :unelevated "unelevated"
               :on-click   (utils/prevent-default #(dispatch [:projects2/new-project]))}
     "Create one"]]])

(defn- listing-component []
  (let [projects (subscribe [:projects2/list])]
    (if (empty? @projects)
      [no-projects-view]
      [projects-list @projects])))

(defn project-section-index []
  (let [create-project-button (ui/main-action {:icon "add" :on-click #(dispatch [:projects2/template-project])})]
    [ui/fixed-width (merge {:action create-project-button} (common2/nav-params))
     [listing-component]]))
