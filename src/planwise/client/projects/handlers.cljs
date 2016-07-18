(ns planwise.client.projects.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [accountant.core :as accountant]
            [planwise.client.routes :as routes]
            [planwise.client.projects.api :as api]))

(def in-projects (path [:projects]))
(def in-current-project (path [:current-project]))

(register-handler
 :projects/search
 in-projects
 (fn [db [_ value]]
   (assoc db :search-string value)))

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
   (api/load-project project-id :projects/project-loaded)
   (assoc db :view-state :loading
             :current nil)))

(register-handler
 :projects/project-loaded
 in-projects
  (fn [db [_ project-data]]
    (assoc db :view-state :view
              :current project-data)))

(register-handler
 :projects/load-projects
 in-projects
 (fn [db [_ project-id]]
   (api/load-projects :projects/projects-loaded)
   (assoc db :view-state :loading-list)))

(register-handler
 :projects/projects-loaded
 in-projects
  (fn [db [_ projects]]
    (assoc db :view-state :list
              :list projects)))

(register-handler
 :projects/create-project
 in-projects
 (fn [db [_ project-data]]
   (api/create-project project-data :projects/project-created)
   (assoc db :view-state :creating)))

(register-handler
 :projects/project-created
 in-projects
 (fn [db [_ project-data]]
   (let [project-id (:id project-data)]
     (when (nil? project-id)
       (throw "Invalid project data"))
     (accountant/navigate! (routes/project-demographics {:id project-id}))
     (assoc db :view-state :view
               :list (cons project-data (:list db))
               :current project-data))))

(register-handler
 :projects/toggle-filter
 in-current-project
 (fn [db [_ filter-group filter-key filter-value]]
   (let [path [filter-group :filters filter-key]
         current-filter (get-in db path)
         toggled-filter (if (contains? current-filter filter-value)
                          (disj current-filter filter-value)
                          (conj current-filter filter-value))
         new-db (assoc-in db path toggled-filter)
         updated-filters (get-in new-db [:facilities :filters])]
     (api/fetch-facilities updated-filters :projects/facilities-loaded)
     new-db)))

(register-handler
 :projects/facilities-loaded
 in-current-project
 (fn [db [_ response]]
   (-> db
       (assoc-in [:facilities :count] (:count response))
       (assoc-in [:facilities :list] (:facilities response)))))

(register-handler
 :projects/update-position
 in-current-project
 (fn [db [_ new-position]]
   (assoc-in db [:map-view :position] new-position)))

(register-handler
 :projects/update-zoom
 in-current-project
 (fn [db [_ new-zoom]]
   (assoc-in db [:map-view :zoom] new-zoom)))
