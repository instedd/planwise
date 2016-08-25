(ns planwise.client.datasets.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [planwise.client.asdf :as asdf]
            [planwise.client.datasets.api :as api]
            [planwise.client.datasets.db :as db]))

(def in-datasets (path [:datasets]))

(defn map-server-status
  [server-status]
  (some-> server-status
          (update :status keyword)
          (update :state keyword)
          (update :result keyword)))

(defn update-datasets
  "Propagates the client states to the updated list of datasets"
  [server-datasets]
  (map (fn [dataset]
         (let [status (map-server-status (:server-status dataset))
               state (db/server-status->state status)]
           (assoc dataset
                  :server-status status
                  :state state)))
       server-datasets))



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
       (assoc :list (update-datasets datasets))
       (assoc-in [:state 0] :loaded))))

(register-handler
 :datasets/search
 in-datasets
 (fn [db [_ value]]
   (assoc db :search-string value)))

(register-handler
 :datasets/cancel-import!
 in-datasets
 (fn [db [_ dataset-id]]
   (c/log (str "Cancelling collection import for dataset " dataset-id))
   (api/cancel-import! dataset-id :datasets/datasets-loaded)
   db))


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
   (let [view-state (get-in db [:state 1])
         new-db (if (= :creating view-state)
                  (assoc-in db [:state 1] :list)
                  db)
         coll-id (:collection-id dataset)]
     ;; optimistic updates: add the new dataset and remove the collection from
     ;; the resmap available list
     (-> new-db
         (update :resourcemap asdf/swap! db/remove-resmap-collection coll-id)
         (update :list #(into % (update-datasets [dataset])))))))
