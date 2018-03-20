(ns planwise.client.projects2.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.asdf :as asdf]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.projects2.db :as db]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]
            [planwise.client.components.common2 :as common2]
            [planwise.client.ui.rmwc :as m]))

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
  (let [name (:name project)
        id   (:id  project)]
    [ui/card {:href (routes/projects2-show {:id id})
              :primary [:img {:src "http://via.placeholder.com/373x278"}]
              :title (or-blank name [:i "Untitled"])}]))

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

(defn- current-project-input
  [label path transform]
  (let [current-project   (subscribe [:projects2/current-project])]
    [m/TextField {:type "text"
                  :label label
                  :on-change #(dispatch [:projects2/save-key path (transform (-> % .-target .-value))])
                  :value (get-in @current-project path)}]))

(defn edit-current-project
  []
  (let [current-project   (subscribe [:projects2/current-project])]
    [ui/fixed-width (common2/nav-params)
      [ui/panel {}
        [m/Grid {}
          [m/GridCell {:span 6}
            [:form.vertical
              [current-project-input "Goal" [:name] identity]
              [current-project-input "Target" [:config :demographics :target] valid-input]
              [current-project-input "Budget" [:config :actions :budget] valid-input]]]]]]))

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
