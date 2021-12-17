(ns planwise.client.projects2.components.common
  (:require [planwise.client.ui.dialog :refer [dialog]]))

(defn delete-project-dialog
  [{:keys [open? id cancel-fn delete-fn]}]
  [dialog {:open?     open?
           :title     "Delete Project"
           :class     "narrow"
           :delete-fn delete-fn
           :cancel-fn cancel-fn
           :content   [:p.dialog-prompt
                       "Do you want to delete this project?"
                       [:br]
                       [:strong "This action cannot be undone."]]}])

(defn reset-project-dialog
  [{:keys [open? id cancel-fn accept-fn]}]
  [dialog {:open?       open?
           :title       "Reset Project"
           :class       "narrow"
           :acceptable? true
           :accept-fn   accept-fn
           :cancel-fn   cancel-fn
           :content     [:p.dialog-prompt
                         "Do you want to reset this project? "
                         "This will delete all your current scenarios, but will allow changes in configuration."
                         [:br]
                         [:strong "This action cannot be undone."]]}])
