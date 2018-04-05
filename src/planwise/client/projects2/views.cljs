(ns planwise.client.projects2.views
  (:require [re-frame.core :refer [subscribe dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.population :refer [population-dropdown-component]]
            [planwise.client.projects2.db :as db]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]
            [planwise.client.coverage :refer [coverage-algorithm-filter-options]]
            [planwise.client.datasets2.components.dropdown :refer [datasets-dropdown-component]]
            [planwise.client.components.common2 :as common2]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.ui.filter-select :as filter-select]
            [planwise.client.utils :as utils]
            [planwise.client.mapping :refer [static-image fullmap-region-geo]]))

(def map-preview-size {:width 373 :height 278})

;;------------------------------------------------------------------------
;;Project listing and creation

(defn- or-blank
  [value fallback]
  (cond
    (nil? value) fallback
    (= value "") fallback
    :else value))

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
              :title (or-blank (:name project) [:i "Untitled"])
              :status (or-blank (:state project) [:i "status: unknown"])}]))

(defn- projects-list
  [projects]
  [ui/card-list {}
   (for [project projects]
     [project-card {:key (:id project)} project])])

(defn- listing-component []
  (let [projects   (subscribe [:projects2/list])]
    (if (nil? @projects)
      [:div
       [:p "You have no projects..."]]
      [projects-list @projects])))

(defn- project-section-index []
  (let [create-project-button (ui/main-action {:icon "add" :on-click #(dispatch [:projects2/new-project])})]
    [ui/fixed-width (merge {:action create-project-button} (common2/nav-params))
     [listing-component]]))

;;------------------------------------------------------------------------
;;Current Project updating

(defn- valid-input
  [inp]
  (let [value (js/parseInt inp)]
    (if (and (number? value) (not (js/isNaN value))) value nil)))

(defn- regions-dropdown-component
  [attrs]
  (let [props (merge {:choices @(rf/subscribe [:regions/list])
                      :label-fn :name
                      :render-fn (fn [region] [:div
                                               [:span (:name region)]
                                               [:span.option-context (:country-name region)]])}
                     attrs)]
    (into [filter-select/single-dropdown] (mapcat identity props))))

(defn- current-project-input
  [label path transform]
  (let [current-project (rf/subscribe [:projects2/current-project])
        value           (or (get-in @current-project path) "")
        change-fn       #(rf/dispatch-sync [:projects2/save-key path (-> % .-target .-value transform)])]
    [m/TextField {:type "text"
                  :label label
                  :on-change change-fn
                  :value value}]))

(defn- current-project-start
  []
  (let [current-project (subscribe [:projects2/current-project])]
    [m/Button {:id "start-project"
               :on-click (utils/prevent-default #(dispatch [:projects2/start-project (:id @current-project)]))}
     (if (= (keyword (:state @current-project)) :started) "Started ..." "Start")]))

(defn edit-current-project
  []
  (let [current-project (subscribe [:projects2/current-project])]
    [ui/fixed-width (common2/nav-params)
     [ui/panel {}
      [m/Grid {}
       [m/GridCell {:span 6}
        [:form.vertical
         [:h2 "Goal"]
         [current-project-input "Goal" [:name] identity]
         [regions-dropdown-component {:label "Region"
                                      :on-change #(dispatch [:projects2/save-key :region-id %])
                                      :model (:region-id @current-project)}]
         [:h2 "Demand"]
         [population-dropdown-component {:label "Sources"
                                         :value (:population-source-id @current-project)
                                         :on-change #(dispatch [:projects2/save-key :population-source-id %])}]
         [current-project-input "Target" [:config :demographics :target] valid-input]
         [current-project-input "Unit" [:config :demographics :unit-name] identity]
         [:h2 "Sites"]
         [datasets-dropdown-component {:label "Dataset"
                                       :value (:dataset-id @current-project)
                                       :on-change #(dispatch [:projects2/save-key :dataset-id %])}]
         [:h2 "Coverage"]
         [coverage-algorithm-filter-options {:coverage-algorithm (:coverage-algorithm @current-project)
                                             :value (get-in @current-project [:config :coverage :filter-options])
                                             :on-change #(dispatch [:projects2/save-key [:config :coverage :filter-options] %])
                                             :empty [:div "First choose dataset."]}]
         [:h2 "Actions"]
         [current-project-input "Budget" [:config :actions :budget] valid-input]]
        [current-project-start]]]]]))

(defn- project-section-show
  []
  (let [page-params       (subscribe [:page-params])
        id                (:id @page-params)
        current-project   (subscribe [:projects2/current-project])]
    (fn []
      (cond
        (= (:id @current-project) (js/parseInt id)) [edit-current-project]
        :else (do
                (dispatch [:projects2/get-project-data id])
                [common2/loading-placeholder])))))

;;------------------------------------------------------------------------
;;Projects view


(defn project2-view []
  (let [page-params  (subscribe [:page-params])
        projects-list (subscribe [:projects2/list])]
    (fn []
      (do
        (when (nil? @projects-list)
          (dispatch [:projects2/projects-list]))
        (let [section      (:section @page-params)]
          (cond
            (= section :index) [project-section-index]
            (= section :show) [project-section-show]
            :else "..."))))))
