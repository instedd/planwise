(ns planwise.client.datasets2.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.datasets2.api :as api]
            [planwise.client.datasets2.db :as db]))

(def in-datasets2 (rf/path [:datasets2]))

;; ----------------------------------------------------------------------------
;; Dataset2 listing

(rf/reg-event-fx
 :datasets2/load-datasets2
 in-datasets2
 (fn [{:keys [db]} [_]]
   {:api (assoc api/load-datasets2
                :on-success [:datasets2/datasets2-loaded])
    :db  (update db :list asdf/reload!)}))


(rf/reg-event-db
 :datasets2/datasets2-loaded
 in-datasets2
 (fn [db [_ datasets2]]
   (update db :list asdf/reset! datasets2)))


;; ----------------------------------------------------------------------------
;; Creating dataset and uploading csv-file

(rf/reg-event-db
 :datasets2/begin-new-dataset
 in-datasets2
 (fn [db _]
   (assoc db
          :view-state :create-dialog
          :last-error nil)))

(rf/reg-event-db
 :datasets2/cancel-new-dataset
 in-datasets2
 (fn [db _]
   (assoc db
          :view-state :list
          :last-error nil)))

(rf/reg-event-fx
 :datasets2/create-load-dataset
 in-datasets2
 (fn [{:keys [db]} [_ {:keys [name csv-file coverage-algorithm]}]]
   {:api (assoc (api/create-dataset-with-csv {:name name
                                              :csv-file csv-file
                                              :coverage-algorithm coverage-algorithm})
                :on-success [:datasets2/dataset-created]
                :on-failure [:datasets2/dataset-not-created])
    :db  (assoc db
                :view-state :creating
                :last-error nil)}))

(rf/reg-event-db
 :datasets2/dataset-created
 in-datasets2
 (fn [db [_ dataset]]
   (-> db
       (assoc :view-state :list)
       (update :list asdf/swap! into [dataset]))))

(rf/reg-event-db
 :datasets2/dataset-not-created
 in-datasets2
 (fn [db [_ err]]
   (assoc db
          :view-state :create-dialog
          :last-error (:status-text err))))
