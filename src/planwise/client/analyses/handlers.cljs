(ns planwise.client.analyses.handlers
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.analyses.api :as api]))

(def in-analyses (rf/path [:analyses]))

;; ----------------------------------------------------------------------------
;; Analyses listing

(rf/reg-event-fx
 :analyses/load-analyses
 in-analyses
 (fn [{:keys [db]} [_]]
   {:api (assoc api/load-analyses
                :on-success [:analyses/analyses-loaded])
    :db  (update db :list asdf/reload!)}))

(rf/reg-event-db
 :analyses/invalidate-analyses
 in-analyses
 (fn [db [_]]
   (update db :list asdf/invalidate!)))

(rf/reg-event-db
 :analyses/analyses-loaded
 in-analyses
 (fn [db [_ analyses]]
   (update db :list asdf/reset! analyses)))

(rf/reg-event-fx
 :analyses/create-analysis!
 in-analyses
 (fn [_ [_]]
   {:api (assoc (api/create-analysis! "Test")
                :on-success [:analyses/invalidate-analyses])}))
