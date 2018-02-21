(ns planwise.client.projects.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [accountant.core :as accountant]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by-id]]
            [planwise.client.projects.api :as api]
            [planwise.client.projects.db :as db]
            [planwise.client.routes :as routes]))

(def in-projects (rf/path [:projects]))

;; ---------------------------------------------------------------------------
;; Project listing

(register-handler
 :projects/load-projects
 in-projects
 (fn [db [_]]
   (api/load-projects :projects/projects-loaded)
   (update db :list asdf/reload!)))

(rf/reg-event-db
 :projects/invalidate-projects
 in-projects
 (fn [db [_ new-projects]]
   (if new-projects
     (update db :list asdf/invalidate! into new-projects)
     (update db :list asdf/invalidate!))))

(rf/reg-event-fx
 :projects/projects-loaded
 in-projects
 (fn [{:keys [db]} [_ projects]]
   (let [region-ids (->> projects
                         (map :region-id)
                         (remove nil?)
                         (set))]
     {:dispatch [:regions/load-regions-with-preview region-ids]
      :db (update db :list asdf/reset! projects)})))

;; Project searching

(rf/reg-event-db
 :projects/search
 in-projects
 (fn [db [_ value]]
   (assoc db :search-string value)))

;; ---------------------------------------------------------------------------
;; Project creation

(rf/reg-event-db
 :projects/begin-new-project
 in-projects
 (fn [db [_]]
   (assoc db :view-state :create-dialog)))

(rf/reg-event-db
 :projects/cancel-new-project
 in-projects
 (fn [db [_]]
   (assoc db :view-state :list)))

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
     (dispatch [:datasets/invalidate-datasets])   ; to update project counts for the selected dataset
     (dispatch [:current-project/project-loaded project-data])
     (accountant/navigate! (routes/project-demographics {:id project-id}))
     (-> db
         (assoc :view-state :list)
         (update :list asdf/invalidate! conj project-data)))))

;; ----------------------------------------------------------------------------
;; Project deletion

(register-handler
 :projects/delete-project
 in-projects
 (fn [db [_ id]]
   (api/delete-project id :projects/project-deleted)
   ;; optimistically delete the project from our list
   (update db :list asdf/swap! remove-by-id id)))

(register-handler
 :projects/leave-project
 in-projects
 (fn [db [_ id]]
   (api/leave-project id :projects/project-deleted)
   ;; optimistically remove the project from our list
   (update db :list asdf/swap! remove-by-id id)))

(rf/reg-event-db
 :projects/project-deleted
 in-projects
 (fn [db [_ data]]
   (let [deleted-id (:deleted data)]
     (update db :list asdf/invalidate! remove-by-id deleted-id))))
