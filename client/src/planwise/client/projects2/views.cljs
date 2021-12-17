(ns planwise.client.projects2.views
  (:require [reagent.core :as r]
            [re-com.core :as rc]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common2 :as common2]
            [planwise.client.projects2.components.common :refer [delete-project-dialog reset-project-dialog]]
            [planwise.client.projects2.components.dashboard :as dashboard]
            [planwise.client.projects2.components.listings :as listings]
            [planwise.client.projects2.components.settings :as settings]
            [planwise.client.projects2.components.create :as create]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]))

(defn- project-section-show
  [section]
  (let [page-params       (subscribe [:page-params])
        current-project   (subscribe [:projects2/current-project])
        open-dialog       (subscribe [:projects2/open-dialog])
        close-dialog-fn   #(dispatch [:projects2/dismiss-dialog])
        delete-project-fn #(dispatch [:projects2/delete-project (:id @current-project)])
        reset-project-fn  #(dispatch [:projects2/reset-project (:id @current-project)])]
    (fn [section]
      (let [id (:id @page-params)]
        [:<>
         (cond
           (not= (:id @current-project) id)      [common2/loading-placeholder]
           (= "draft" (:state @current-project)) [settings/edit-current-project @page-params]
           :else                                 [dashboard/view-current-project section])
         [delete-project-dialog {:open?     (= :delete @open-dialog)
                                 :cancel-fn close-dialog-fn
                                 :delete-fn delete-project-fn}]
         [reset-project-dialog {:open?     (= :reset @open-dialog)
                                :cancel-fn close-dialog-fn
                                :accept-fn reset-project-fn}]]))))

;;------------------------------------------------------------------------
;;Projects view


(defn project2-view []
  (let [page-params   (subscribe [:page-params])
        projects-list (subscribe [:projects2/list])]
    (fn []
      (let [section (:section @page-params)]
        (case section
          :index             [listings/project-section-index]
          :new               [create/project-section-template-selector]
          :show              [project-section-show :scenarios]
          :project-scenarios [project-section-show :scenarios]
          :project-settings  [project-section-show :settings]
          [common2/loading-placeholder])))))
