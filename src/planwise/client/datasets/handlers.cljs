(ns planwise.client.datasets.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [planwise.client.datasets.api :as api]
            [planwise.client.db :as db]))

(def in-datasets (path [:datasets]))

(defn initialised?
  [db]
  (let [state (:state db)]
    (not (or (nil? state) (= :initialising state)))))

(defn status->state
  [status]
  (let [status (if (coll? status) (first status) status)]
    (case status
      "importing" :importing
      "ready" :ready
      :ready)))

(register-handler
 :datasets/initialise!
 in-datasets
 (fn [db [_]]
   (if-not (initialised? db)
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
   (-> db
       (assoc-in [:resourcemap :authorised?] (:authorised? datasets-info))
       (assoc-in [:resourcemap :collections] (:collections datasets-info))
       (assoc :state (status->state (:status datasets-info))
              :raw-status (:status datasets-info)
              :facility-count (:facility-count datasets-info)))))

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
     (api/import-collection! coll-id type-field :datasets/import-running)
     (assoc db
            :state :importing
            :raw-status [:importing :starting]))))

(register-handler
 :datasets/import-running
 in-datasets
 (fn [db [_ info]]
   (let [state (status->state (:status info))]
     (if (= :importing state)
       (do
         (.setTimeout js/window
                      #(api/importer-status :datasets/import-running)
                      3000))
       (do
         (c/log "Import finished")
         (dispatch [:projects/fetch-facility-types])
         (dispatch [:datasets/reload-info])))
     (assoc db
            :state state
            :raw-status (:status info)))))
