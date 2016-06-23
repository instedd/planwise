(ns planwise.client.projects.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [accountant.core :as accountant]
            [planwise.client.routes :as routes]))


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

(register-handler
 :projects/create-project
 in-projects
 (fn [db [_]]
   ;; FIXME
   (dispatch [:projects/project-created {:id 1 :name "Test Project"}])
   (assoc db :creating? false)))

(register-handler
 :projects/project-created
 in-projects
 (fn [db [_ project-data]]
   (let [project-id (:id project-data)]
     (when (nil? project-id)
       (throw "Invalid project data"))
     (accountant/navigate! (routes/project-demographics {:id project-id}))
     (assoc-in db [:cache project-id] project-data))))
