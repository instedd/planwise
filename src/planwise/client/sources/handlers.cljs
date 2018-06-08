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
