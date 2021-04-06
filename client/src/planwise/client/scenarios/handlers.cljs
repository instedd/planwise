(ns planwise.client.scenarios.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [clojure.string :as s]
            [planwise.client.asdf :as asdf]
            [planwise.client.routes :as routes]
            [planwise.client.utils :as utils]
            [planwise.client.scenarios.api :as api]
            [planwise.client.scenarios.db :as db]))

(def in-scenarios (rf/path [:scenarios]))

;; Controller

(routes/reg-controller
 {:id            :scenario
  :params->state (fn [{:keys [page id]}]
                   (when (= :scenarios page) id))
  :start         [:scenarios/get-scenario]
  :stop          [:scenarios/clear-current-scenario]})

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

;; Views stack

(rf/reg-event-db
 :scenarios/push-view
 in-scenarios
 (fn [db [_ view]]
   (println [".......x" view (:views-stack db)])
   (update-in db [:views-stack] #(conj % view))))

(rf/reg-event-db
 :scenarios/pop-view
 in-scenarios
 (fn [db [_]]
   (println [".......z" (:views-stack db)])
   (update-in db [:views-stack] #(if (empty? %) % (pop %)))))

(rf/reg-event-db
 :scenarios/reset-state
 in-scenarios
 (fn [db [_]]
   (assoc-in db [:views-stack] [])))

(defn peek-view
  [{:keys [views-stack]}]
  (if (empty? views-stack)
    :current-scenario
    (peek views-stack)))

(defn peek-view-1
  [{:keys [views-stack]}]
  (if (< (count views-stack) 2)
    :current-scenario
    (peek (pop views-stack))))

;; Loading scenario view

(rf/reg-event-fx
 :scenarios/scenario-not-found
 (fn [_ _]
   {:navigate (routes/home)}))

(rf/reg-event-fx
 :scenarios/load-scenario
 (fn [{:keys [db]} [_ {:keys [id]}]]
   (let [project-id    (get-in db [:projects2 :current-project :id])]
     {:dispatch [:scenarios/clear-current-scenario]
      :navigate (routes/scenarios {:project-id project-id :id id})})))

;; fields that may change when the deferred computation of demand finishes
(def demand-fields [:state :demand-coverage :increase-coverage :effort :raster :label :sources-data :providers-data :error-message :source-demand :population-under-coverage])

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
 in-scenarios
 (fn [{:keys [db]} [_ scenario]]
   (let [current-scenario (:current-scenario db)
         should-update    (= (:id current-scenario) (:id scenario))]
     (if should-update
       (merge {:db (assoc db :current-scenario
                          (merge current-scenario
                                 (select-keys scenario demand-fields)))}
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

;; This event should be used when first loading the scenario, not for updating
;; its contents
(rf/reg-event-fx
 :scenarios/get-scenario
 in-scenarios
 (fn [{:keys [db]} [_ id]]
   {:api (assoc (api/load-scenario id)
                :on-success [:scenarios/save-current-scenario-and-track]
                :on-failure [:scenarios/scenario-not-found])
    :dispatch [:scenarios/reset-state]}))

(rf/reg-event-fx
 :scenarios/copy-scenario
 in-scenarios
 (fn [{:keys [db]} [_ id]]
   {:dispatch [:scenarios/invalidate-scenarios]
    :api  (assoc (api/copy-scenario id)
                 :on-success [:scenarios/load-scenario])}))


(rf/reg-event-fx
 :scenarios/clear-current-scenario
 in-scenarios
 (fn [{:keys [db]} [_]]
   {:db       (assoc db :current-scenario nil)
    :dispatch [:scenarios/reset-state]}))

;; Editing scenario

(rf/reg-event-fx
 :scenarios/open-rename-dialog
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [name (get-in db [:current-scenario :name])]
     {:db       (assoc db :rename-dialog {:value name})
      :dispatch-n [[:scenarios/pop-view]
                   [:scenarios/push-view :rename-dialog]]})))

(rf/reg-event-fx
 :scenarios/cancel-dialog
 in-scenarios
 (fn [{:keys [db]} [_]]
   {:db (assoc db
               :changeset-dialog nil
               :rename-dialog nil)
    :dispatch [:scenarios/pop-view]}))

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
                (assoc-in [:current-scenario :name] name))
      :dispatch [:scenarios/pop-view]})))

(defn new-provider-name
  [changeset]
  (let [new-providers (filter #(= (:action %) "create-provider") changeset)]
    (if (empty? new-providers)
      "New provider 0"
      (let [vals (mapv (fn [p] (->> (:name p) (re-find #"\d+") int)) new-providers)]
        (str "New provider " (inc (apply max vals)))))))

(rf/reg-event-fx
 :scenarios/create-provider
 in-scenarios
 (fn [{:keys [db]} [_ location suggestion]]
   (let [{:keys [current-scenario]} db
         new-action   (db/new-action {:location location
                                      :name (new-provider-name (:changeset current-scenario))} :create)
         new-provider (merge (db/new-provider-from-change new-action) suggestion)
         updated-scenario (dissoc current-scenario
                                  :computing-best-locations)]
     {:api  (assoc (api/update-scenario (:id current-scenario) updated-scenario)
                   :on-success [:scenarios/update-demand-information])
      :db   (assoc  db
                    :current-scenario  updated-scenario)
      :dispatch [:scenarios/open-changeset-dialog new-provider]})))

(rf/reg-event-fx
 :scenarios/open-changeset-dialog
 in-scenarios
 (fn [{:keys [db]} [_ change]]
   {:db (assoc db :changeset-dialog  change)
    :dispatch [:scenarios/push-view :changeset-dialog]}))

(rf/reg-event-fx
 :scenarios/accept-changeset-dialog
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [current-scenario  (get-in db [:current-scenario])
         updated-provider  (get-in db [:changeset-dialog])
         new-change?       (nil? (utils/find-by-id (:changeset current-scenario) (:id updated-provider)))
         updated-scenario  (update current-scenario
                                   :changeset
                                   (fn [c]
                                     (if new-change?
                                       (conj (vec c) (:change updated-provider))
                                       (utils/replace-by-id c (:change updated-provider)))))]
     {:api  (assoc (api/update-scenario (:id current-scenario) updated-scenario)
                   :on-success [:scenarios/update-demand-information])
      :db   (-> db
                (assoc-in [:current-scenario] updated-scenario))
      :dispatch [:scenarios/pop-view]})))

(rf/reg-event-fx
 :scenarios/delete-change
 in-scenarios
 (fn [{:keys [db]} [_ id]]
   (let [current-scenario (:current-scenario db)
         modified-changeset (utils/remove-by-id (:changeset current-scenario) id)
         updated-scenario (assoc current-scenario :changeset modified-changeset)]
     {:api  (assoc (api/update-scenario (:id current-scenario) updated-scenario)
                   :on-success [:scenarios/update-demand-information])
      :db   (assoc db :current-scenario updated-scenario)
      :dispatch [:scenarios/pop-view]})))

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

(rf/reg-event-db
 :scenarios/change-sort-column-order
 in-scenarios
 (fn [db [_ column order]]
   (assoc db
          :sort-column column
          :sort-order order)))

;; ----------------------------------------------------------------------------
;; Providers in map

(rf/reg-event-fx
 :scenarios.map/select-provider
 in-scenarios
 (fn [{:keys [db]} [_ provider]]
   (let [id (get-in db [:current-scenario :id])]
     (when
      (not= (:id provider)
            (get-in db [:selected-provider :id]))
       {:db (assoc db :selected-provider provider)
        :api (assoc (api/get-provider-geom id (:id provider))
                    :on-success [:scenarios/update-geometry])}))))

(rf/reg-event-db
 :scenarios/update-geometry
 in-scenarios
 (fn [db [_ geom]]
   (update db :selected-provider #(merge % geom))))

(rf/reg-event-db
 :scenarios.map/unselect-provider
 in-scenarios
 (fn [db [_ provider]]
   (assoc db :selected-provider nil)))

(rf/reg-event-fx
 :scenarios.map/select-suggestion
 in-scenarios
 (fn [{:keys [db]} [_ suggestion]]
   {:db (assoc db :selected-suggestion suggestion)}))

(rf/reg-event-db
 :scenarios.map/unselect-suggestion
 in-scenarios
 (fn [db [_ _]]
   (assoc db :selected-suggestion nil)))

;;Creating new-providers

;TODO; check when pending state for resquested suggestions
(rf/reg-event-fx
 :scenarios.new-action/toggle-options
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [actual-state (peek-view db)
         getting-suggested-locations? (get-in db [:current-scenario :computing-best-locations :state])
         getting-suggested-providers? (get-in db [:current-scenario :computing-best-improvements :state])
         dispatch-event               (fn [e] {:dispatch [:scenarios.new-action/abort-fetching-suggestions e]})
         next-state (case actual-state
                      :current-scenario                 :show-options-to-create-provider
                      :show-scenario-settings           :show-options-to-create-provider
                      :show-options-to-create-provider  :current-scenario
                      :new-provider                     :current-scenario
                      :new-suggestion                   :current-scenario
                      :new-intervention                 :current-scenario
                      :get-suggestions-for-new-provider :current-scenario
                      :get-suggestions-for-improvements :current-scenario
                      actual-state)]
     (merge
      {:db (-> db
               (assoc-in [:current-scenario :suggested-locations] nil))}
      (cond
        getting-suggested-locations? (dispatch-event :computing-best-locations)
        getting-suggested-providers? (dispatch-event :computing-best-improvements)
        (= next-state :current-scenario) {:dispatch [:scenarios/pop-view]}
        :else {:dispatch [:scenarios/push-view next-state]})))))

(rf/reg-event-fx
 :scenarios.new-provider/fetch-suggested-locations
 in-scenarios
 (fn [{:keys [db]} [_]]
   {:db  (-> db
             (assoc-in [:current-scenario :computing-best-locations :state] :suggestions-request))
    :api (assoc (api/suggested-locations-for-new-provider
                 (get-in db [:current-scenario :id]))
                :on-success [:scenarios/suggested-locations]
                :on-failure [:scenarios/no-suggested-locations]
                :key :suggestions-request)
    :dispatch-n [[:scenarios/pop-view]
                 [:scenarios/push-view :get-suggestions-for-new-provider]]}))

(rf/reg-event-fx
 :scenarios/suggested-locations
 (fn [{:keys [db]} [_ suggestions]]
   (let [state (get-in db [:scenarios :current-scenario :computing-best-locations :state])
         suggestions' (map-indexed (fn [index suggestion]
                                     (assoc suggestion :ranked (inc index)))
                                   suggestions)]
     (if (some? state)
       {:db (-> db
                (assoc-in [:scenarios :current-scenario :suggested-locations] suggestions')
                (assoc-in [:scenarios :current-scenario :computing-best-locations :state] nil))
        :dispatch-n [[:scenarios/pop-view]
                     [:scenarios/push-view :new-suggestion]]}

       {}))))

(rf/reg-event-fx
 :scenarios/no-suggested-locations
 in-scenarios
 (fn [{:keys [db]} [_ {:keys [response]}]]
   (let [state (get-in db [:current-scenario :computing-best-locations :state])]
     {:db (if (some? state)
            (do
              (js/alert (or (:error response) "Could not compute suggestions"))
              (-> db
                  (assoc-in [:current-scenario :computing-best-locations :state] nil)))
            db)
      :dispatch [:scenarios/pop-view]})))

(rf/reg-event-fx
 :scenarios.new-action/simple-create-provider
 in-scenarios
 (fn [{:keys [db]} [_]]
   {:dispatch-n [[:scenarios/pop-view]
                 [:scenarios/push-view :new-provider]]}))

(rf/reg-event-fx
 :scenarios.new-action/abort-fetching-suggestions
 in-scenarios
 (fn [{:keys [db]} [_ request-action-name]]
   (let [request-key (get-in db [:current-scenario request-action-name :state])]
     {:db (-> db (assoc-in [:current-scenario request-action-name :state] nil))
      :api-abort request-key
      :dispatch [:scenarios/pop-view]})))

(rf/reg-event-fx
 :scenarios/edit-change
 in-scenarios
 (fn [{:keys [db]} [_ change]]
   {:db (assoc db
               :changeset-dialog  change)
    :dispatch [:scenarios/push-view :changeset-dialog]}))

(rf/reg-event-fx
 :scenarios/delete-current-scenario
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [id (get-in db [:current-scenario :id])]
     {:api (assoc (api/delete-scenario id)
                  :on-success [:projects2/project-scenarios])
      :dispatch [:scenarios/load-scenarios]})))

(rf/reg-event-fx
 :scenarios/open-delete-dialog
 in-scenarios
 (fn [{:keys [db]} [_]]
   {:dispatch-n [[:scenarios/pop-view]
                 [:scenarios/push-view :delete-scenario]]}))

(rf/reg-event-fx
 :scenarios/show-scenario-settings
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [state (peek-view db)]
     {:dispatch (if (= state :show-scenario-settings)
                  [:scenarios/pop-view]
                  [:scenarios/push-view :show-scenario-settings])})))

(rf/reg-event-fx
 :scenarios.new-action/fetch-suggested-providers-to-improve
 in-scenarios
 (fn [{:keys [db]} [_]]
   {:db  (-> db
             (assoc-in [:current-scenario :computing-best-improvements :state] :suggestions-request))
    :api (assoc (api/suggested-providers-to-improve
                 (get-in db [:current-scenario :id]))
                :on-success [:scenarios/suggested-interventions]
                :on-failure [:scenarios/no-suggested-interventions]
                :key :suggestions-request)
    :dispatch-n [[:scenarios/pop-view]
                 [:scenarios/push-view :get-suggestions-for-improvements]]}))

(rf/reg-event-fx
 :scenarios/suggested-interventions
 (fn [{:keys [db]} [_ suggestions]]
   (let [state (get-in db [:scenarios :current-scenario :computing-best-improvements :state])]
     (if (some? state)
       {:db (-> db
                (assoc-in [:scenarios :current-scenario :suggested-providers] suggestions)
                (assoc-in [:scenarios :current-scenario :computing-best-improvements :state] nil))
        :dispatch-n [[:scenarios/pop-view]
                     [:scenarios/push-view :new-intervention]]}
       {}))))

(rf/reg-event-fx
 :scenarios/no-suggested-interventions
 in-scenarios
 (fn [{:keys [db]} [_ {:keys [response]}]]
   (let [state (get-in db [:current-scenario :computing-best-improvements :state])]
     {:db (if (some? state)
            (do
              (js/alert (or (:error response) "Could not compute suggestions"))
              (-> db
                  (assoc-in [:current-scenario :computing-best-improvements :state] nil)))
            db)
      :dispatch [:scenarios/pop-view]})))

(rf/reg-event-fx
 :scenarios/edit-suggestion
 in-scenarios
 (fn [{:keys [db]} [_ suggestion]]
   (let [view-state (peek-view db)]
     {:dispatch (if (or (= view-state :new-provider) (= view-state :new-suggestion))
                  [:scenarios/create-provider (:location suggestion) {:required-capacity (:action-capacity suggestion)}]
                  [:scenarios/edit-change suggestion])})))
