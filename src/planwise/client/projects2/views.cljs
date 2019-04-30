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
  [section]
  (let [page-params       (subscribe [:page-params])
        current-project   (subscribe [:projects2/current-project])]
    (fn [section]
      (let [id (:id @page-params)]
        (cond
          (not= (:id @current-project) id) (do
                                             (dispatch [:projects2/get-project id])
                                             [common2/loading-placeholder])
          (= "draft" (:state @current-project)) [settings/edit-current-project]
          :else [dashboard/view-current-project section])))))

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
          (case section
            :index [listings/project-section-index]
            :new [listings/project-section-new]
            :show [project-section-show :scenarios]
            :project-scenarios [project-section-show :scenarios]
            :project-settings [project-section-show :settings]
            [common2/loading-placeholder]))))))
