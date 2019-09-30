(ns planwise.client.sources.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.sources.api :as api]))

(def in-sources (rf/path [:sources]))

(rf/reg-event-fx
 :sources/load
 in-sources
 (fn [{:keys [db]} [_]]
   {:api (assoc api/load-sources
                :on-success [:sources/loaded])
    :db  (update db :list asdf/reload!)}))

(rf/reg-event-db
 :sources/loaded
 in-sources
 (fn [db [_ sources]]
   (update db :list asdf/reset! sources)))

(rf/reg-event-db
 :sources.new/update
 in-sources
 (fn [db [_ changes]]
   (update db :new #(merge % changes))))

(rf/reg-event-fx
 :sources.new/create
 in-sources
 (fn [{:keys [db]}]
   (let [new-source (get db :new)]
     {:api (assoc (api/create-source-with-csv new-source)
                  :on-success [:sources.new/created]
                  :on-failure [:sources.new/failed])})))

(rf/reg-event-db
 :sources.new/discard
 in-sources
 (fn [db]
   (rf/dispatch [:modal/hide])
   (dissoc db :new)))

(rf/reg-event-db
 :sources.new/created
 in-sources
 (fn [db [_ created-source]]
   (rf/dispatch [:modal/hide])
   (-> db
       (dissoc :new)
       (update :list #(asdf/swap! % conj created-source)))))

(rf/reg-event-db
 :sources.new/failed
 in-sources
 (fn [db [_ err]]
   (assoc-in db [:new :current-error] (:status-text err))))
