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
 :datasets2/invalidate-datasets2
 in-datasets2
 (fn [db [_]]
   (update db :list asdf/invalidate!)))

(rf/reg-event-db
 :datasets2/datasets2-loaded
 in-datasets2
 (fn [db [_ datasets2]]
   (update db :list asdf/reset! datasets2)))

;; ----------------------------------------------------------------------------
;; New dataset dialog

(rf/reg-event-fx
 :datasets2/create-dataset
 in-datasets2
  (fn [{:keys [db]}  [_ name]]
    {:api (assoc (api/create-dataset2! name)
                :on-success [:datasets2/dataset-created])}))

(rf/reg-event-db
 :datasets2/dataset-created
 in-datasets2
 (fn [db [_ dataset]]
    (update db :list asdf/swap! into [dataset])))

;; ----------------------------------------------------------------------------
;; Uploading csv-file

(rf/reg-event-fx
  :datasets2/load-sites
  in-datasets2
  (fn [{:keys [db]} [_ js-file]]
    (let [form-data (doto
                        (js/FormData.)
                        (.append "file" js-file))]
      {:api (assoc (api/load-csv-file! form-data)
                  :on-success [:datasets2/sites-loaded])})))

(rf/reg-event-db
 :datasets2/sites-loaded
 in-datasets2
 (fn [db [_ sites]]
    (update db :sites asdf/swap! into [sites])))

;; ----------------------------------------------------------------------------
;; Creating dataset and uploading csv-file

(rf/reg-event-fx
  :datasets2/create-load-dataset
  in-datasets2
  (fn [{:keys [db]} [_ name js-file]]
    (let [form-data (doto
                        (js/FormData.)
                        (.append "name" name)
                        (.append "file" js-file))]
      {:api (assoc (api/creating-dataset-with-uploaded-sites! form-data)
                   :on-success [:datasets2/dataset-created])})))
