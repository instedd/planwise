(ns planwise.client.projects2.components.common
  (:require [planwise.client.dialog :refer [new-dialog]]
            [planwise.client.components.common2 :as common2]))

(defn delete-project-dialog
  [state id]
  [new-dialog {:open? @state
               :title "Delete Project"
               :delete-dispatch [:projects2/delete-project id]
               :cancel-fn #(reset! state false)
               :content [:p "Do you want to delete this project?"]}])
