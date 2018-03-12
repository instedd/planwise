(ns planwise.client.projects2.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.asdf :as asdf]
            [reagent.core :as r]
            [planwise.client.projects2.db :as db]
            [planwise.client.components.common :as common]))

;;New Project-View
;; --------------------------------

(defn- project-section-index []
  [:div
   [:div.title
     [:h1 "New Project"]]
   [:button.primary
     {:on-click #(dispatch [:projects2/new-project])}
      "Create"]])

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

(defn project2-view []
  (let [page-params  (subscribe [:page-params])]
    (fn []
      (let [section      (:section @page-params)]
        [:article
          (cond
            (= section :index) [project-section-index]
            (= section :show) [project-section-show]
            :else "...")]))))
