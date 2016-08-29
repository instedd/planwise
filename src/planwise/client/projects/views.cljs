(ns planwise.client.projects.views
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.asdf :as asdf]
            [planwise.client.projects.db :as db]
            [planwise.client.components.common :as common]
            [planwise.client.projects.components.new-project :refer [new-project-dialog]]
            [planwise.client.projects.components.listing :refer [no-projects-view projects-list]]))

(defn project-list-page []
  (let [view-state (subscribe [:projects/view-state])
        projects (subscribe [:projects/list])
        filtered-projects (subscribe [:projects/filtered-list])]
    (fn []
      (let [list (asdf/value @projects)]
        (when (asdf/should-reload? @projects)
          (dispatch [:projects/load-projects]))
        [:article.project-list
         (cond
           (nil? list) [common/loading-placeholder]
           (empty? list) [no-projects-view]
           :else [projects-list @filtered-projects])
         (when (db/show-dialog? @view-state)
           [common/modal-dialog {:on-backdrop-click
                                 #(dispatch [:projects/cancel-new-project])}
            [new-project-dialog]])]))))
