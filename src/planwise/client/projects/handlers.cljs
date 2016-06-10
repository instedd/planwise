(ns planwise.client.projects.handlers
  (:require [re-frame.core :refer [register-handler path]]))


(def in-projects (path [:projects]))

(register-handler
 :projects/begin-new-project
 in-projects
 (fn [db [_]]
   (assoc db :creating? true)))

(register-handler
 :projects/cancel-new-project
 in-projects
 (fn [db [_]]
   (assoc db :creating? false)))
