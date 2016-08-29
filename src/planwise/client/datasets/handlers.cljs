(ns planwise.client.datasets.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [clojure.string :refer [blank?]]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by-id]]
            [planwise.client.datasets.api :as api]
            [planwise.client.datasets.db :as db]))

(def in-datasets (path [:datasets]))

(defn map-server-status
  [server-status]
  (some-> server-status
          (update :status keyword)
          (update :state keyword)))

(defn map-datasets
  [server-datasets]
  (mapv #(update % :server-status map-server-status) server-datasets))


;; ----------------------------------------------------------------------------
;; Dataset listing

(register-handler
 :datasets/load-datasets
 in-datasets
 (fn [db [_]]
   (api/load-datasets :datasets/datasets-loaded)
   (update db :list asdf/reload!)))

(register-handler
 :datasets/invalidate-datasets
 in-datasets
 (fn [db [_]]
   (update db :list asdf/invalidate!)))

(register-handler
 :datasets/refresh-datasets
 in-datasets
 (fn [db [_ time]]
   (let [sets (asdf/value (:list db))
         statuses (map :server-status sets)
         any-running? (some some? statuses)
         refresh-interval (if any-running? 1000 10000)
         last-refresh (or (:last-refresh db) 0)
         since-last-refresh (- time last-refresh)]
     (if (< refresh-interval since-last-refresh)
       (-> db
           (update :list asdf/invalidate!)
           (assoc :last-refresh time))
       db))))

(register-handler
 :datasets/datasets-loaded
 in-datasets
 (fn [db [_ datasets]]
   (update db :list asdf/reset! (map-datasets datasets))))

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
   (assoc db
          :state :create-dialog
          :new-dataset-data nil)))

(register-handler
 :datasets/cancel-new-dataset
 in-datasets
 (fn [db [_]]
   (assoc db :state :list)))

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
         description (if (blank? description)
                       (str "Imported from Resourcemap collection " coll-id)
                       description)
         type-field (get-in db [:new-dataset-data :type-field])]
     (api/create-dataset! name description coll-id type-field
                          :datasets/dataset-created :datasets/create-failed))
   (assoc db :state :creating)))

(register-handler
 :datasets/dataset-created
 in-datasets
 (fn [db [_ dataset]]
   (let [view-state (:state db)
         new-db (if (= :creating view-state)
                  (assoc db :state :list)
                  db)
         coll-id (:collection-id dataset)]
     ;; optimistic updates: add the new dataset and remove the collection from
     ;; the resmap available list
     (-> new-db
         (update :resourcemap asdf/swap! db/remove-resmap-collection coll-id)
         ;; TODO: under some circumstances, we might be optimistically adding a
         ;; duplicate dataset here; if an invalidation and reload run between
         ;; the API call and this event. We should check that the dataset is not
         ;; already there.
         (update :list asdf/swap! into (map-datasets [dataset]))))))

(register-handler
 :datasets/create-failed
 in-datasets
 (fn [db [_ error-info]]
   (c/error (str "Dataset creation failed: " error-info))
   (assoc db :state :list)))

;; ----------------------------------------------------------------------------
;; Dataset deletion

(register-handler
 :datasets/delete-dataset
 in-datasets
 (fn [db [_ dataset-id]]
   (api/delete-dataset! dataset-id :datasets/dataset-deleted)
   ;; optimistic update: remove the dataset from the list and invalidate resmap infomation
   ;; to make the collection available again
   (-> db
       (update :list asdf/swap! remove-by-id dataset-id)
       (update :resourcemap asdf/invalidate!))))

(register-handler
 :datasets/dataset-deleted
 in-datasets
 (fn [db [_ data]]
   (dispatch [:datasets/invalidate-datasets])
   db))
