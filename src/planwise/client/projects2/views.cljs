(ns planwise.client.projects2.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.asdf :as asdf]
            [reagent.core :as r]
            [planwise.client.projects2.db :as db]
            [planwise.client.routes :as routes]
            [planwise.client.components.common :as common]))

;;------------------------------------------------------------------------
;;Project listing and creation

(defn project-card
  [project]
  (let [name (:name project)
        id   (:id  project)]
    [:div.project-card
      [:a
        {:href (routes/projects2-show {:id id})} name]]))

(defn projects-list
  [projects] 
  [:ul.project-list
    (for [project projects]
      [:li {:key (:id project)}
        [project-card project]])])

(defn- listing-component []
   (let [projects   (subscribe [:projects2/list])]
    (if (nil? @projects)
      [:div
        [common/icon :box]
        [:p "You have no projects..."]]
      [projects-list @projects])))

(defn- create-new-project []
  [:div
    [:div.title
      [:h1 "New Project"]]
    [:button.primary
     {:on-click #(dispatch [:projects2/new-project])}
       "Create"]])
 
(defn- project-section-index []
  [:div
    [create-new-project] 
    [listing-component]])

;;------------------------------------------------------------------------
;;Current Project updating

(defn- update-component []
  (let [current-project   (subscribe [:projects2/current-project])]
      [:input  {:type "text"
                :placeholder "Goal..."
                :value (:name @current-project)
                :on-change #(dispatch [:projects2/save-data :name (-> % .-target .-value)])}]))


(defn- project-section-show []
  (let [page-params       (subscribe [:page-params])
        id                (:id @page-params)
        current-project   (subscribe [:projects2/current-project])]
    (fn []
      (cond
        (= (:id @current-project) (js/parseInt id))[update-component]
        :else (do
                (dispatch [:projects2/get-project-data id])
                [common/loading-placeholder])))))

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
          [:article
            (cond
              (= section :index) [project-section-index]
              (= section :show) [project-section-show]
              :else "...")])))))
