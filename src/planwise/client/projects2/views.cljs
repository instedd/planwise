(ns planwise.client.projects2.views
  (:require [re-frame.core :refer [subscribe dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.projects2.components.listings :as listings]
            [planwise.client.projects2.components.settings :as settings]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]
            [planwise.client.components.common2 :as common2]))

(defn- project-section-show
  []
  (let [page-params       (subscribe [:page-params])
        id                (:id @page-params)
        current-project   (subscribe [:projects2/current-project])]
    (fn []
      (cond
        (= (:id @current-project) (js/parseInt id)) [settings/edit-current-project]
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
            (= section :index) [listings/project-section-index]
            (= section :show) [project-section-show]
            :else [common2/loading-placeholder]))))))
