(ns planwise.client.scenarios.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.scenarios.api :as api]
            [planwise.client.scenarios.db :as db]))

(def in-scenarios (rf/path [:scenarios]))

(rf/reg-event-db
 :scenarios/save-current-scenario
 in-scenarios
 (fn [db [_ scenario]]
   (assoc db :current-scenario scenario)))

(rf/reg-event-fx
 :scenarios/load-scenario
 in-scenarios
 (fn [{:keys [db]} [_ id]]
   {:api (assoc (api/load-scenario id)
                :on-success [:scenarios/save-current-scenario])}))

(rf/reg-event-fx
 :scenarios/create-new-scenario
 in-scenarios
 (fn [{:keys [db]} [_ current-scenario]]
  {:api  (assoc (api/create-scenario current-scenario)
                :on-success [:scenarios/load-scenario])}))
