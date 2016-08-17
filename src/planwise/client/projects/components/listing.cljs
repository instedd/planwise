(ns planwise.client.projects.components.listing
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as str]
            [planwise.client.utils :as utils]
            [planwise.client.routes :as routes]
            [planwise.client.mapping :refer [static-image]]))

(defn new-project-button []
  [:button.primary
   {:on-click
    #(dispatch [:projects/begin-new-project])}
   "New Project"])

(defn search-box [projects-count show-new]
  (let [search-string (subscribe [:projects/search-string])]
    (fn [projects-count show-new]
      [:div.search-box
       [:div (utils/pluralize projects-count "project")]
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


(defn project-stat [title stat]
  [:div.stat
   [:div.stat-title title]
   [:div.stat-value stat]])


(defn project-stats
  [{:keys [facilities-total facilities-targeted]} region-population]
  [:div.project-stats
   (project-stat "Target Facilities"
                 (str (or facilities-targeted 0) " / " (or facilities-total 0)))
   (project-stat "Region polulation"
                 (.toLocaleString region-population "en"))])

(defn project-card [{:keys [id goal region-id region-name stats region-population] :as project}]
  (let [region-geo (subscribe [:regions/preview-geojson region-id])]
    (fn [{:keys [id goal region-id region-name stats] :as project}]
      [:a {::href (routes/project-facilities project)}
        [:div.project-card
          [:div.project-card-content
           [:h1 goal]
           [:h2 (str "at " region-name)]
           [project-stats stats region-population]]
          (if-not (str/blank? @region-geo)
            [:img.map-preview {:src (static-image @region-geo)}])]])))

(defn projects-list [projects]
  [:ul.projects-list
    (for [project projects]
      [:li {:key (:id project)}
        [project-card project]])])
