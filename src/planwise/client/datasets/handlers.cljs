(ns planwise.client.datasets.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [planwise.client.datasets.api :as api]
            [planwise.client.datasets.db :as db]))

(def in-datasets (path [:datasets]))

(defn map-server-status
  [server-status]
  (-> server-status
      (update :status keyword)
      (update :state keyword)
      (update :result keyword)))

(register-handler
 :datasets/initialise!
 in-datasets
 (fn [db [_]]
   (if-not (db/initialised? (:state db))
     (api/load-datasets-info :datasets/info-loaded)
     (assoc db :state :initialising))
   db))

(register-handler
 :datasets/reload-info
 in-datasets
 (fn [db [_]]
   (c/log "Reloading datasets information")
   (api/load-datasets-info :datasets/info-loaded)
   (assoc db :selected db/empty-datasets-selected)))

(register-handler
 :datasets/info-loaded
 in-datasets
 (fn [db [_ datasets-info]]
   (let [server-status (map-server-status (:status datasets-info))]
     (-> db
         (assoc-in [:resourcemap :authorised?] (:authorised? datasets-info))
         (assoc-in [:resourcemap :collections] (:collections datasets-info))
         (assoc :state (db/server-status->state server-status)
                :server-status server-status
                :facility-count (:facility-count datasets-info))))))

(register-handler
 :datasets/select-collection
 in-datasets
 (fn [db [_ coll]]
   (if-not (= (:id coll) (get-in db [:selected :collection :id]))
     (do
       (api/load-collection-info (:id coll) :datasets/collection-info-loaded)
       (-> db
           (assoc-in [:selected :collection] coll)
           (assoc-in [:selected :type-field] nil)
           (assoc-in [:selected :fields] nil)))
     db)))

(register-handler
 :datasets/collection-info-loaded
 in-datasets
 (fn [db [_ collection-info]]
   (-> db
       (assoc-in [:selected :fields] (:fields collection-info))
       (assoc-in [:selected :valid?] (:valid? collection-info)))))

(register-handler
 :datasets/select-type-field
 in-datasets
 (fn [db [_ field]]
   (assoc-in db [:selected :type-field] field)))

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
        new-state (db/server-status->state status)]
    ;; refresh critical system information if an import finished executing
    (when (and (or (db/importing? current-state) (db/cancelling? current-state))
               (= :ready new-state))
      (dispatch [:projects/fetch-facility-types])
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
