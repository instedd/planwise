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
   (assoc db :view-state :create-dialog)))

(register-handler
 :projects/cancel-new-project
 in-projects
 (fn [db [_]]
   (assoc db :view-state :view)))

(register-handler
 :projects/load-project
 in-projects
 (fn [db [_ project-id]]
   (async-handle (api/load-project project-id)
                 #(dispatch [:projects/project-loaded %]))
   (assoc db :view-state :loading)))

(register-handler
 :projects/create-project
 in-projects
 (fn [db [_ project-data]]
   (async-handle (api/create-project project-data)
                 #(dispatch [:projects/project-created %]))
   (assoc db :view-state :creating)))

(register-handler
 :projects/project-loaded
 in-projects
  (fn [db [_ project-data]]
    (assoc db :view-state :view
              :current project-data)))

(register-handler
 :projects/project-created
 in-projects
 (fn [db [_ project-data]]
   (let [project-id (:id project-data)]
     (when (nil? project-id)
       (throw "Invalid project data"))
     (accountant/navigate! (routes/project-demographics {:id project-id}))
     (assoc db :view-state :view
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
