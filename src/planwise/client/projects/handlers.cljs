(ns planwise.client.projects.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [accountant.core :as accountant]
            [planwise.client.routes :as routes]
            [planwise.client.projects.api :as api]
            [clojure.string :refer [split capitalize join]]
            [planwise.client.db :as db]))

(def in-projects (path [:projects]))
(def in-current-project (path [:projects :current]))
(def in-filter-definitions (path [:filter-definitions]))

(register-handler
 :projects/fetch-facility-types
 (fn [db [_]]
   (api/fetch-facility-types :projects/facility-types-received)
   db))

(register-handler
 :projects/facility-types-received
 in-filter-definitions
 (fn [db [_ types]]
   (assoc db :facility-type types)))

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
             :current db/empty-project-viewmodel)))

(register-handler
 :projects/project-loaded
 in-projects
  (fn [db [_ project-data]]
    (dispatch [:regions/load-regions-with-geo [(:region-id project-data)]])
    (assoc db :view-state :view
              :current (db/project-viewmodel project-data))))

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
    (let [region-ids (->> projects
                        (map :region-id)
                        (remove nil?)
                        (set))]
      (dispatch [:regions/load-regions-with-geo region-ids])
      (assoc db :view-state :list
                :list projects))))

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
               :current (db/project-viewmodel project-data)))))

(defn- facilities-criteria [current-project-db]
  (let [filters (get-in current-project-db [:facilities :filters])
        project-region-id (get-in current-project-db [:project-data :region-id])]
    (assoc filters :region project-region-id)))

(register-handler
 :projects/toggle-filter
 in-current-project
  (fn [db [_ filter-group filter-key filter-value]]
    (let [path [filter-group :filters filter-key]
          current-filter (get-in db path)
          toggled-filter (if (contains? current-filter filter-value)
                           (disj current-filter filter-value)
                           (conj current-filter filter-value))
          new-db (-> db
                     (assoc-in path (set toggled-filter))
                     (assoc-in [:facilities :isochrones] nil))
          criteria (facilities-criteria new-db)
          filters (db/project-filters new-db)
          project-id (get-in db [:project-data :id])]
      (api/update-project-filters project-id filters)
      (api/fetch-facilities criteria :projects/facilities-loaded)
      new-db)))

(register-handler
 :projects/load-isochrones
 in-current-project
 (fn [db [_ force?]]
   (when (or force? (nil? (get-in db [:facilities :isochrones])))
     (if-let [time (get-in db [:transport :time])]
       (api/fetch-facilities-with-isochrones (facilities-criteria db) {:threshold time} :projects/isochrones-loaded)))
   db))

(register-handler
 :projects/set-transport-time
 in-current-project
  (fn [db [_ time]]
    (let [new-db (assoc-in db [:transport :time] time)
          filters (db/project-filters new-db)
          project-id (get-in db [:project-data :id])]
      (api/update-project-filters project-id filters)
      (dispatch [:projects/load-isochrones :force])
      new-db)))

(register-handler
 :projects/isochrones-loaded
 in-current-project
 (fn [db [_ response]]
   (assoc-in db [:facilities :isochrones] (->> response
                                            (map :isochrone)
                                            (set)
                                            (filterv some?)))))

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
