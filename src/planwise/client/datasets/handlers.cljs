(ns planwise.client.datasets.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [clojure.string :refer [blank?]]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by-id]]
            [planwise.client.datasets.api :as api]
            [planwise.client.datasets.db :as db]
            [planwise.client.utils :as utils]))

(def in-datasets (rf/path [:datasets]))

;; ----------------------------------------------------------------------------
;; Dataset listing

(rf/reg-event-fx
 :datasets/load-datasets
 in-datasets
 (fn [{:keys [db]} [_]]
   {:api (assoc api/load-datasets
                :on-success [:datasets/datasets-loaded])
    :db  (update db :list asdf/reload!)}))

(rf/reg-event-db
 :datasets/invalidate-datasets
 in-datasets
 (fn [db [_]]
   (update db :list asdf/invalidate!)))

(rf/reg-event-db
 :datasets/refresh-datasets
 in-datasets
 (fn [db [_ time]]
   (let [sets								(asdf/value (:list db))
         statuses           (map :server-status sets)
         any-running?       (some some? statuses)
         refresh-interval   (if any-running? 1000 10000)
         last-refresh       (or (:last-refresh db) 0)
         since-last-refresh (- time last-refresh)]
     (if (< refresh-interval since-last-refresh)
       (-> db
           (update :list asdf/invalidate!)
           (assoc :last-refresh time))
       db))))

(rf/reg-event-db
 :datasets/datasets-loaded
 in-datasets
 (fn [db [_ datasets]]
   (update db :list asdf/reset! datasets)))

(rf/reg-event-db
 :datasets/search
 in-datasets
 (fn [db [_ value]]
   (assoc db :search-string value)))

(rf/reg-event-fx
 :datasets/cancel-import!
 in-datasets
 (fn [_ [_ dataset-id]]
   (rf/console :log "Cancelling collection import for dataset " dataset-id)
   {:api (assoc (api/cancel-import! dataset-id)
                :on-success [:datasets/datasets-loaded])}))


;; ----------------------------------------------------------------------------
;; New dataset dialog

(rf/reg-event-db
 :datasets/begin-new-dataset
 in-datasets
 (fn [db [_]]
   (assoc db
          :state :create-dialog
          :new-dataset-data nil)))

(rf/reg-event-db
 :datasets/cancel-new-dataset
 in-datasets
 (fn [db [_]]
   (assoc db :state :list)))

(rf/reg-event-fx
 :datasets/load-resourcemap-info
 in-datasets
 (fn [{:keys [db]} [_]]
   {:api (assoc api/load-resourcemap-info
                :on-success [:datasets/resourcemap-info-loaded])
    :db  (update db :resourcemap asdf/reload!)}))

(rf/reg-event-db
 :datasets/resourcemap-info-loaded
 in-datasets
 (fn [db [_ data]]
   (update db :resourcemap asdf/reset! (select-keys data [:authorised? :collections]))))

(rf/reg-event-db
 :datasets/update-new-dataset
 in-datasets
 (fn [db [_ field value]]
   (assoc-in db [:new-dataset-data field] value)))

(rf/reg-event-fx
 :datasets/create-dataset
 in-datasets
 (fn [{:keys [db]} [_]]
   (let [collection  (get-in db [:new-dataset-data :collection])
         coll-id     (:id collection)
         name        (:name collection)
         description (:description collection)
         description (if (blank? description)
                       (str "Imported from Resourcemap collection " coll-id)
                       description)
         type-field  (get-in db [:new-dataset-data :type-field])]
     {:api (assoc (api/create-dataset! name description coll-id type-field)
                  :on-success [:datasets/dataset-created]
                  :on-failure [:datasets/create-failed])
      :db  (assoc db :state :creating)})))

(rf/reg-event-db
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
         (update :list asdf/swap! into [dataset])))))

(rf/reg-event-db
 :datasets/create-failed
 in-datasets
 (fn [db [_ error-info]]
   (rf/console :error "Dataset creation failed: " error-info)
   (assoc db :state :list)))

;; ----------------------------------------------------------------------------
;; Dataset deletion and update

(rf/reg-event-fx
 :datasets/delete-dataset
 in-datasets
 (fn [{:keys [db]} [_ dataset-id]]
   {:api (assoc (api/delete-dataset! dataset-id)
                :on-success [:datasets/dataset-deleted])
    ;; optimistic update: remove the dataset from the list and invalidate resmap infomation
    ;; to make the collection available again
    :db  (-> db
             (update :list asdf/swap! remove-by-id dataset-id)
             (update :resourcemap asdf/invalidate!))}))

(rf/reg-event-fx
 :datasets/dataset-deleted
 in-datasets
 (fn [_ _]
   {:dispatch [:datasets/invalidate-datasets]}))

(rf/reg-event-fx
 :datasets/update-dataset
 in-datasets
 (fn [_ [_ dataset-id]]
   {:api (assoc (api/update-dataset! dataset-id)
                :on-success [:datasets/dataset-updated])}))

(rf/reg-event-db
 :datasets/dataset-updated
 in-datasets
 (fn [db [_ dataset]]
   (update db :list asdf/swap! utils/replace-by-id dataset)))
