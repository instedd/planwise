(ns planwise.client.projects2.components.common
  (:require [planwise.client.dialog :refer [dialog]]
            [re-frame.core :refer [dispatch]]
            [planwise.client.components.common2 :as common2]))

(defn delete-project-dialog
  [{:keys [open? id cancel-fn delete-fn]}]
  [dialog {:open? open?
           :title "Delete Project"
           :accept-fn delete-fn
           :cancel-fn cancel-fn
           :content [:p "Do you want to delete this project?"]}])
