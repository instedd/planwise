(ns planwise.client.projects2.views
  (:require [reagent.core :as r]
            [re-com.core :as rc]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common2 :as common2]
            [planwise.client.projects2.components.dashboard :as dashboard]
            [planwise.client.projects2.components.listings :as listings]
            [planwise.client.projects2.components.settings :as settings]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]))

(defn- project-section-show
  []
  (let [page-params       (subscribe [:page-params])
        current-project   (subscribe [:projects2/current-project])]
    (fn []
      (let [id (:id @page-params)]
        (cond
          (not= (:id @current-project) id) (do
                                             (dispatch [:projects2/get-project id])
                                             [common2/loading-placeholder])
          (= "draft" (:state @current-project)) [settings/edit-current-project]
          :else [dashboard/view-current-project])))))

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
            (= section :index) [listings/project-section-index]
            (= section :show) [project-section-show]
            :else [common2/loading-placeholder]))))))
