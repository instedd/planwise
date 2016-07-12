(ns planwise.client.projects.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [accountant.core :as accountant]
            [planwise.client.common :refer [debounced async-handle]]
            [planwise.client.routes :as routes]
            [planwise.client.projects.api :as api]))


(def in-projects (path [:projects]))
(def in-current-project (path [:current-project]))

(register-handler
 :projects/begin-new-project
 in-projects
 (fn [db [_]]
   (assoc db :creating? true
             :creating-waiting? false)))

(register-handler
 :projects/cancel-new-project
 in-projects
 (fn [db [_]]
   (assoc db :creating? false)))

(register-handler
 :projects/load-project
 in-projects
 (fn [db [_ project-id]]
   (async-handle (api/load-project project-id)
                 #(dispatch [:projects/project-loaded %]))
   (assoc db :loading? true)))

(register-handler
 :projects/create-project
 in-projects
 (fn [db [_ project-data]]
   (async-handle (api/create-project project-data)
                 #(dispatch [:projects/project-created %]))
   (assoc db :creating-waiting? true)))

(register-handler
 :projects/project-loaded
 in-projects
  (fn [db [_ project-data]]
    (assoc db :loading? false
              :current project-data)))

(register-handler
 :projects/project-created
 in-projects
 (fn [db [_ project-data]]
   (let [project-id (:id project-data)]
     (when (nil? project-id)
       (throw "Invalid project data"))
     (accountant/navigate! (routes/project-demographics {:id project-id}))
     (assoc db :creating-waiting? false
               :creating? false
               :current project-data))))

(register-handler
 :projects/toggle-filter
 in-current-project
 (fn [db [_ filter-group filter-key filter-value]]
   (let [path [filter-group :filters filter-key]
         current-filter (get-in db path)
         toggled-filter (if (contains? current-filter filter-value)
                          (disj current-filter filter-value)
                          (conj current-filter filter-value))]
     (assoc-in db path toggled-filter))))
