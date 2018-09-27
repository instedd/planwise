(ns planwise.client.providers-set.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
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

(rf/reg-event-fx
 :providers-set/provider-set-created
 in-providers-set
 (fn [{:keys [db]} [_ provider-set]]
   {:db (assoc db :view-state :list)
    :dispatch [:providers-set/load-providers-set]}))

(rf/reg-event-db
 :providers-set/provider-set-not-created
 in-providers-set
 (fn [db [_ err]]
   (assoc db
          :view-state :create-dialog
          :last-error (:status-text err))))

(rf/reg-event-db
 :providers-set/select-provider-set
 in-providers-set
 (fn [db [_ provider-set]]
   (assoc db
          :view-state :delete-dialog
          :selected-provider provider-set)))

(rf/reg-event-fx
 :providers-set/delete-provider-set
 in-providers-set
 (fn [{:keys [db]} [_]]
   (let [id (get-in db [:selected-provider :id])]
     {:api (assoc (api/delete-provider-set id)
                  :on-success [:providers-set/cancel-delete-dialog]
                  :on-failure [:providers-set/alert-delete-dialog])})))

(rf/reg-event-fx
 :providers-set/cancel-delete-dialog
 in-providers-set
 (fn [{:keys [db]} [_]]
   {:db (assoc db :view-state :list
               :selected-provider nil)
    :dispatch [:providers-set/load-providers-set]}))

(rf/reg-event-db
 :providers-set/alert-delete-dialog
 in-providers-set
 (fn [db [_ {:keys [response]}]]
   (let [list (asdf/value (:list db))
         name (:name (utils/find-by-id list (:provider-set-id response)))]
     (js/alert (str "Can not delete " name))
     (assoc db
            :view-state :list
            :selected-provider nil))))