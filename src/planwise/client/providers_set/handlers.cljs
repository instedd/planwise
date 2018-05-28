(ns planwise.client.providers-set.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.providers-set.api :as api]
            [planwise.client.providers-set.db :as db]))

(def in-providers-set (rf/path [:providers-set]))

;; ----------------------------------------------------------------------------
;; provider-set2 listing

(rf/reg-event-fx
 :providers-set/load-providers-set
 in-providers-set
 (fn [{:keys [db]} [_]]
   {:api (assoc api/load-providers-set
                :on-success [:providers-set/providers-set-loaded])
    :db  (update db :list asdf/reload!)}))


(rf/reg-event-db
 :providers-set/providers-set-loaded
 in-providers-set
 (fn [db [_ providers-set]]
   (update db :list asdf/reset! providers-set)))


;; ----------------------------------------------------------------------------
;; Creating provider-set and uploading csv-file

(rf/reg-event-db
 :providers-set/begin-new-provider-set
 in-providers-set
 (fn [db _]
   (assoc db
          :view-state :create-dialog
          :new-provider-set db/initial-new-provider-set
          :last-error nil)))

(rf/reg-event-db
 :providers-set/new-provider-set-update
 in-providers-set
 (fn [db [_ key value]]
   (assoc-in db [:new-provider-set key] value)))

(rf/reg-event-db
 :providers-set/cancel-new-provider-set
 in-providers-set
 (fn [db _]
   (assoc db
          :view-state :list
          :last-error nil)))

(rf/reg-event-fx
 :providers-set/create-load-provider-set
 in-providers-set
 (fn [{:keys [db]} [_ {:keys [name csv-file coverage-algorithm]}]]
   {:api (assoc (api/create-provider-set-with-csv {:name name
                                                   :csv-file csv-file
                                                   :coverage-algorithm coverage-algorithm})
                :on-success [:providers-set/provider-set-created]
                :on-failure [:providers-set/provider-set-not-created])
    :db  (assoc db
                :view-state :creating
                :last-error nil)}))

(rf/reg-event-db
 :providers-set/provider-set-created
 in-providers-set
 (fn [db [_ provider-set]]
   (-> db
       (assoc :view-state :list)
       (update :list asdf/swap! into [provider-set]))))

(rf/reg-event-db
 :providers-set/provider-set-not-created
 in-providers-set
 (fn [db [_ err]]
   (assoc db
          :view-state :create-dialog
          :last-error (:status-text err))))
