(ns planwise.client.scenarios.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.routes :as routes]
            [planwise.client.scenarios.api :as api]
            [planwise.client.scenarios.db :as db]))

(def in-scenarios (rf/path [:scenarios]))

;; Db events

(rf/reg-event-db
 :scenarios/save-current-scenario
 in-scenarios
 (fn [db [_ scenario]]
   (assoc db :current-scenario scenario)))

(rf/reg-event-db
 :scenarios/save-key
 in-scenarios
 (fn [db [_ path value]]
   (assoc-in db path value)))

;; Loading scenario view

(rf/reg-event-fx
 :scenarios/scenario-not-found
 (fn [_ _]
   {:navigate (routes/home)}))

(rf/reg-event-fx
 :scenarios/load-scenario
 (fn [{:keys [db]} [_ {:keys [id]}]]
   (let [project-id    (get-in db [:projects2 :current-project :id])]
     {:navigate (routes/scenarios {:project-id project-id :id id})})))

;; fields that may change when the deferred computation of demand finishes
(def demand-fields [:state :demand-coverage :increase-coverage :investment :raster :label :changeset :sources-data])

(defn- dispatch-track-demand-information-if-needed
  [scenario]
  (let [should-track (= (:state scenario) "pending")]
    (cond
      should-track {:delayed-dispatch {:ms 1000
                                       :key [:track-demand-information]
                                       :dispatch [:scenarios/track-demand-information (:id scenario)]}}
      :else {})))

;; Updates the current scenario (if it's still the same one passed by the argument)
;; and if the scenario is still in pending, schedule a next dispatch for tracking
;; the processing status
(rf/reg-event-fx
 :scenarios/update-demand-information
 (fn [{:keys [db]} [_ scenario]]
   (let [current-scenario (get-in db [:scenarios :current-scenario])
         should-update    (= (:id current-scenario) (:id scenario))]
     (if should-update
       (merge {:db (-> db (assoc-in [:scenarios :current-scenario]
                                    (merge current-scenario (select-keys scenario demand-fields))))}
              (dispatch-track-demand-information-if-needed scenario))
       {}))))

(rf/reg-event-fx
 :scenarios/track-demand-information
 (fn [_ [_ id]]
   {:api (assoc (api/load-scenario id)
                :on-success [:scenarios/update-demand-information])}))

(rf/reg-event-fx
 :scenarios/save-current-scenario-and-track
 (fn [_ [_ scenario]]
   (merge
    {:dispatch [:scenarios/save-current-scenario scenario]}
    (dispatch-track-demand-information-if-needed scenario))))

(rf/reg-event-fx
 :scenarios/get-scenario
 (fn [_ [_ id]]
   {:api (assoc (api/load-scenario id)
                :on-success [:scenarios/save-current-scenario-and-track]
                :on-failure [:scenarios/scenario-not-found])}))

(rf/reg-event-fx
 :scenarios/copy-scenario
 in-scenarios
 (fn [{:keys [db]} [_ id]]
   {:dispatch [:scenarios/invalidate-scenarios]
    :api  (assoc (api/copy-scenario id)
                 :on-success [:scenarios/load-scenario])}))

(rf/reg-event-db
 :scenarios/clear-current-scenario
 in-scenarios
 (fn [db [_]]
   (assoc db :current-scenario nil)))

;; Editing scenario

(rf/reg-event-db
 :scenarios/open-rename-dialog
 in-scenarios
 (fn [db [_]]
   (let [name (get-in db [:current-scenario :name])]
     (assoc db
            :view-state :rename-dialog
            :rename-dialog {:value name}))))

(rf/reg-event-db
 :scenarios/cancel-rename-dialog
 in-scenarios
 (fn [db [_]]
   (assoc db
          :view-state :current-scenario
          :rename-dialog nil)))

(rf/reg-event-fx
 :scenarios/accept-rename-dialog
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [name (get-in db [:rename-dialog :value])
         current-scenario (assoc (:current-scenario db) :name name)]
     {:api  (assoc (api/update-scenario (:id current-scenario) current-scenario)
                   :on-success [:scenarios/invalidate-scenarios])
      :db   (-> db
                ;; do not reset rename-dialog to nil or dialog animation after <enter> will fail
                (assoc-in [:current-scenario :name] name)
                (assoc-in [:view-state] :current-scenario))})))

;;Creating new-providers

(def request-key [:scenarios :current-scenario :computing-best-locations :request])

(rf/reg-event-fx
 :scenarios.new-provider/toggle-select-location
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [view-state (get-in db [:view-state])
         next-state (if (= view-state :new-provider) ; is creating new provider?
                      :current-scenario
                      :new-provider)]
     (merge {:db (-> db
                     (assoc-in [:view-state] next-state)
                     (assoc-in [:current-scenario :suggested-locations] nil))}
            (if (= next-state :new-provider)
              {:dispatch [:scenarios.new-provider/get-suggested-providers]}
              {:api-abort request-key})))))

(rf/reg-event-fx
 :scenarios.new-provider/get-suggested-providers
 in-scenarios
 (fn [{:keys [db]} [_]]
   {;FIXME: Issue #456
    :db  (assoc-in db [:current-scenario :computing-best-locations :state] true)
    :api (assoc (api/suggested-providers (get-in db [:current-scenario :id]))
                :on-success [:scenarios/suggested-providers]
                :on-failure [:scenarios/no-suggested-providers]
                :key        request-key)}))

(rf/reg-event-db
 :scenarios/suggested-providers
 in-scenarios
 (fn [db [_ suggestions]]
   (-> db
       (assoc-in [:current-scenario :suggested-locations] suggestions)
       (assoc-in [:current-scenario :computing-best-locations :state] false))))

(rf/reg-event-db
 :scenarios/no-suggested-providers
 in-scenarios
 (fn [db [_]]
   (assoc-in db [:current-scenario :computing-best-locations :state] false)))

(rf/reg-event-fx
 :scenarios/create-provider
 in-scenarios
 (fn [{:keys [db]} [_ {:keys [lat lon]}]]
   (let [{:keys [current-scenario]} db
         new-provider (db/initial-provider {:location {:lat lat :lon lon}})
         updated-scenario (dissoc (update current-scenario :changeset #(conj % new-provider)) :suggested-locations :computing-best-locations)
         new-provider-index (dec (count (:changeset updated-scenario)))]
     {:api  (assoc (api/update-scenario (:id current-scenario) updated-scenario)
                   :on-success [:scenarios/update-demand-information])
      :db   (-> db
                (assoc :current-scenario updated-scenario))
      :dispatch [:scenarios/open-changeset-dialog new-provider-index]})))

(rf/reg-event-db
 :scenarios/open-changeset-dialog
 in-scenarios
 (fn [db [_ changeset-index]]
   (assoc db
          :view-state        :changeset-dialog
          :view-state-params {:changeset-index changeset-index}
          :changeset-dialog  (get-in db [:current-scenario :changeset changeset-index]))))

(rf/reg-event-fx
 :scenarios/accept-changeset-dialog
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [current-scenario  (get-in db [:current-scenario])
         changeset-index   (get-in db [:view-state-params :changeset-index])
         updated-changeset (get-in db [:changeset-dialog])
         updated-scenario  (assoc-in current-scenario [:changeset changeset-index] updated-changeset)]
     {:api  (assoc (api/update-scenario (:id current-scenario) updated-scenario)
                   :on-success [:scenarios/update-demand-information])
      :db   (-> db
                (assoc-in [:current-scenario] updated-scenario)
                (assoc-in [:view-state] :current-scenario))})))

(rf/reg-event-db
 :scenarios/cancel-changeset-dialog
 in-scenarios
 (fn [db [_]]
   (assoc db
          :view-state :current-scenario
          :changeset-dialog nil)))

(rf/reg-event-fx
 :scenarios/delete-provider
 in-scenarios
 (fn [{:keys [db]} [_ index]]
   (let [current-scenario (:current-scenario db)
         deleted-changeset (vec (keep-indexed #(if (not= %1 index) %2) (:changeset current-scenario)))
         updated-scenario (assoc current-scenario :changeset deleted-changeset)]
     {:api  (assoc (api/update-scenario (:id current-scenario) updated-scenario)
                   :on-success [:scenarios/update-demand-information])
      :db   (assoc db :current-scenario updated-scenario)
      :dispatch [:scenarios/cancel-changeset-dialog]})))

;; ----------------------------------------------------------------------------
;; Scenarios listing

(rf/reg-event-fx
 :scenarios/load-scenarios
 (fn [{:keys [db]} [_]]
   (let [project-id (get-in db [:projects2 :current-project :id])]
     {:api (assoc (api/load-scenarios project-id)
                  :on-success [:scenarios/scenarios-loaded])
      :db  (update-in db [:scenarios :list] asdf/reload!)})))

(rf/reg-event-db
 :scenarios/scenarios-loaded
 in-scenarios
 (fn [db [_ scenarios]]
   (update db :list asdf/reset! scenarios)))

(rf/reg-event-db
 :scenarios/invalidate-scenarios
 in-scenarios
 (fn [db [_]]
   (update db :list asdf/invalidate!)))

;; ----------------------------------------------------------------------------
;; Providers in map

(rf/reg-event-db
 :scenarios.map/select-provider
 in-scenarios
 (fn [db [_ provider]]
   (assoc db :selected-provider provider)))

(rf/reg-event-db
 :scenarios.map/unselect-provider
 in-scenarios
 (fn [db [_ provider]]
   (assoc db :selected-provider nil)))
