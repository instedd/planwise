(ns planwise.client.datasets.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [planwise.client.asdf :as asdf]
            [planwise.client.datasets.api :as api]
            [planwise.client.datasets.db :as db]))

(def in-datasets (path [:datasets]))

(defn map-server-status
  [server-status]
  (-> server-status
      (update :status keyword)
      (update :state keyword)
      (update :result keyword)))

;; ----------------------------------------------------------------------------
;; Dataset listing

(register-handler
 :datasets/load-datasets
 in-datasets
 (fn [db [_]]
   (let [list-state (get-in db [:state 0])]
     (if-not (#{:loaded :loading :reloading} list-state)
       (do
         (api/load-datasets :datasets/datasets-loaded)
         (assoc-in db [:state 0] :loading))
       db))))

(register-handler
 :datasets/invalidate-datasets
 in-datasets
 (fn [db [_]]
   (let [list-state (get-in db [:state 0])]
     (if (= :loaded list-state)
       (assoc-in db [:state 0] :invalid)
       db))))

(register-handler
 :datasets/datasets-loaded
 in-datasets
 (fn [db [_ datasets]]
   (-> db
       (assoc :list datasets)
       (assoc-in [:state 0] :loaded))))

(register-handler
 :datasets/search
 in-datasets
 (fn [db [_ value]]
   (assoc db :search-string value)))


;; ----------------------------------------------------------------------------
;; New dataset dialog

(register-handler
 :datasets/begin-new-dataset
 in-datasets
 (fn [db [_]]
   (-> db
       (assoc-in [:state 1] :create-dialog)
       (assoc :new-dataset-data nil))))

(register-handler
 :datasets/cancel-new-dataset
 in-datasets
 (fn [db [_]]
   (assoc-in db [:state 1] :list)))

(register-handler
 :datasets/load-resourcemap-info
 in-datasets
 (fn [db [_]]
   (api/load-resourcemap-info :datasets/resourcemap-info-loaded)
   (update db :resourcemap asdf/reload!)))

(register-handler
 :datasets/resourcemap-info-loaded
 in-datasets
 (fn [db [_ data]]
   (update db :resourcemap asdf/reset! (select-keys data [:authorised? :collections]))))

(register-handler
 :datasets/update-new-dataset
 in-datasets
 (fn [db [_ field value]]
   (assoc-in db [:new-dataset-data field] value)))

(register-handler
 :datasets/create-dataset
 in-datasets
 (fn [db [_]]
   (let [collection (get-in db [:new-dataset-data :collection])
         coll-id (:id collection)
         name (:name collection)
         description (:description collection)
         type-field (get-in db [:new-dataset-data :type-field])]
     (api/create-dataset! name description coll-id type-field :datasets/dataset-created))
   (assoc-in db [:state 1] :creating)))

(register-handler
 :datasets/dataset-created
 in-datasets
 (fn [db [_ dataset]]
   (dispatch [:datasets/invalidate-datasets])
   (let [view-state (get-in db [:state 1])
         new-db (if (= :creating view-state)
                  (assoc-in db [:state 1] :list)
                  db)
         coll-id (:collection-id dataset)]
     ;; optimistic updates: add the new dataset and remove the collection from
     ;; the resmap available list
     (-> new-db
         (update :resourcemap asdf/swap! db/remove-resmap-collection coll-id)
         (update :list #(conj % dataset))))))


;; ----------------------------------------------------------------------------
;; Old handlers
;; TODO: review!

(register-handler
 :datasets/start-import!
 in-datasets
 (fn [db [_]]
   (let [coll-id (get-in db [:selected :collection :id])
         type-field (get-in db [:selected :type-field])]
     (c/log "Started collection import")
     (api/import-collection! coll-id type-field
                             :datasets/import-status-received :datasets/request-failed)
     (assoc db :state :import-requested))))

(register-handler
 :datasets/cancel-import!
 in-datasets
 (fn [db [_]]
   (c/log "Cancelling collection import")
   (api/cancel-import! :datasets/import-status-received :datasets/request-failed)
   (assoc db :state :cancel-requested)))

(register-handler
 :datasets/request-failed
 in-datasets
 (fn [db [_ error-info]]
   (c/log (str "Error performing server request: " error-info))
   (let [state (:state db)
         new-state (case state
                     :import-requested :ready
                     :cancel-requested :importing
                     state)]
     (assoc db :state new-state))))

(defn update-server-status
  [db status]
  (let [status (map-server-status status)
        current-state (:state db)
        current-dataset-id (get-in current-state [:project :current :project-data :dataset-id])
        new-state (db/server-status->state status)]
    ;; refresh critical system information if an import finished executing
    (when (and (or (db/importing? current-state) (db/cancelling? current-state))
               (= :ready new-state))
      (when current-dataset-id
        (dispatch [:projects/fetch-facility-types current-dataset-id]))
      (dispatch [:datasets/reload-info]))
    (assoc db
           :state new-state
           :server-status status)))

(register-handler
 :datasets/import-status-received
 in-datasets
 (fn [db [_ status]]
   (update-server-status db status)))

(register-handler
 :datasets/async-status-received
 in-datasets
 (fn [db [_ status]]
   (if-not (db/request-pending? (:state db))
     (update-server-status db status)
     db)))

(register-handler
 :datasets/update-import-status
 in-datasets
 (fn [db [_]]
   (let [state (:state db)]
     (when (and (db/initialised? state)
                (not (db/request-pending? state)))
       (api/importer-status :datasets/async-status-received)))
   db))
