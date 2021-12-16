(ns planwise.client.scenarios.handlers
  (:require [re-frame.core :refer [register-handler dispatch] :as rf]
            [clojure.string :as s]
            [planwise.common :as common]
            [planwise.client.asdf :as asdf]
            [planwise.client.routes :as routes]
            [planwise.client.utils :as utils]
            [planwise.client.scenarios.api :as api]
            [planwise.client.scenarios.db :as db]))

(def in-scenarios (rf/path [:scenarios]))

;;; Controller

(routes/reg-controller
 {:id            :scenario
  :params->state (fn [{:keys [page id]}]
                   (when (= :scenarios page) id))
  :start         [:scenarios/get-scenario]
  :stop          [:scenarios/clear-current-scenario]})

;;; Generic data handlers

(rf/reg-event-fx
 :scenarios/save-current-scenario
 in-scenarios
 (fn [{:keys [db]} [_ scenario]]
   {:db (assoc db :current-scenario scenario)}))

(rf/reg-event-db
 :scenarios/save-key
 in-scenarios
 (fn [db [_ path value]]
   (assoc-in db path value)))


;;; Loading scenario view

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
(def demand-fields [:state
                    :demand-coverage
                    :increase-coverage
                    :effort
                    :raster
                    :label
                    :sources-data
                    :providers-data
                    :error-message
                    :source-demand
                    :population-under-coverage])

(defn- dispatch-track-demand-information-if-needed
  [scenario]
  (when (= (:state scenario) "pending")
    {:delayed-dispatch {:ms       1000
                        :key      [:track-demand-information]
                        :dispatch [:scenarios/track-demand-information (:id scenario)]}}))

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
       (merge {:db       (assoc db :current-scenario
                                (merge current-scenario
                                       (select-keys scenario demand-fields)))
               :dispatch [:scenarios/refresh-search-providers]}
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
   {:db  (assoc db :view-state :current-scenario)
                                        ; reset view state to close any pending dialogs
    :api (assoc (api/load-scenario id)
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
   (merge db
          {:current-scenario    nil
           :coverage-cache      nil
           :selected-provider   nil
           :selected-suggestion nil
           :suggestions         (:suggestions db/initial-db)
           :providers-search    nil
           :view-state          :current-scenario
           :open-dialog         nil})))


;; Sidebar expansion

(rf/reg-event-db
 :scenarios/expand-sidebar
 in-scenarios
 (fn [db [_]]
   (assoc db :view-state :show-actions-table)))

(rf/reg-event-db
 :scenarios/collapse-sidebar
 in-scenarios
 (fn [db [_]]
   (assoc db :view-state :current-scenario)))


;;; Editing scenario -- Dialogs

;; Closes any dialog open
(rf/reg-event-db
 :scenarios/cancel-dialog
 in-scenarios
 (fn [db [_]]
   (assoc db
          :open-dialog      nil
          :changeset-dialog nil
          :rename-dialog    nil)))

;;; Rename scenario

(rf/reg-event-db
 :scenarios/open-rename-dialog
 in-scenarios
 (fn [db [_]]
   (let [name (get-in db [:current-scenario :name])]
     (assoc db
            :open-dialog   :rename-scenario
            :rename-dialog {:value name}))))

(rf/reg-event-fx
 :scenarios/accept-rename-dialog
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [name             (get-in db [:rename-dialog :value])
         current-scenario (assoc (:current-scenario db) :name name)]
     {:api (assoc (api/update-scenario (:id current-scenario) current-scenario)
                  :on-success [:scenarios/invalidate-scenarios])
      :db  (-> db
               ;; do not reset rename-dialog to nil or dialog animation after <enter> will fail
               (assoc-in [:current-scenario :name] name)
               (assoc-in [:open-dialog] nil))})))


;;; Delete scenario

(rf/reg-event-db
 :scenarios/open-delete-dialog
 in-scenarios
 (fn [db [_]]
   (assoc db :open-dialog :delete-scenario)))

(rf/reg-event-fx
 :scenarios/delete-current-scenario
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [id (get-in db [:current-scenario :id])]
     {:api      (assoc (api/delete-scenario id)
                       :on-success [:scenarios/scenario-deleted])})))

(rf/reg-event-fx
 :scenarios/scenario-deleted
 in-scenarios
 (fn [{:keys [db]} _]
   {:dispatch-n [[:scenarios/load-scenarios]
                 [:projects2/project-scenarios]]}))


;;; Scenario action editing (changesets)

(defn new-provider-name
  [changeset]
  (let [new-providers (filter #(= (:action %) "create-provider") changeset)]
    (if (empty? new-providers)
      "New 0"
      (let [vals (mapv (fn [p] (->> (:name p) (re-find #"\d+") int)) new-providers)]
        (str "New " (inc (apply max vals)))))))

(rf/reg-event-fx
 :scenarios/create-provider
 in-scenarios
 (fn [{:keys [db]} [_ location suggestion]]
   (let [current-scenario  (:current-scenario db)
         new-name          (new-provider-name (:changeset current-scenario))
         required-capacity (or (:required-capacity suggestion) 0)
         new-action        (db/new-action {:location location
                                           :name     new-name
                                           :capacity required-capacity}
                                          :create)
         new-provider      (merge (db/new-provider-from-change new-action) suggestion)]
     {:dispatch [:scenarios/create-change-in-dialog new-provider]})))

(defn- compute-available-budget
  "Computes the remaining budget for the current scenario *without* considering
  the provider investment"
  [db provider]
  (let [project (get-in db [:projects2 :current-project])]
    (when (common/project-has-budget? project)
      (let [scenario       (get-in db [:scenarios :current-scenario])
            initial-budget (get-in project [:config :actions :budget])
            spent-budget   (get-in scenario [:effort])
            investment     (or (get-in provider [:change :investment]) 0)]
        (+ (- initial-budget spent-budget)
           investment)))))

(rf/reg-event-db
 :scenarios/create-change-in-dialog
 (fn [db [_ provider keep-state?]]
   (-> db
       (assoc-in [:scenarios :open-dialog]      :scenario-changeset)
       (assoc-in [:scenarios :changeset-dialog] {:provider         (db/provider-with-change provider)
                                                 :available-budget (compute-available-budget db nil)
                                                 :new-change?      true
                                                 :reset-state?     (not keep-state?)}))))

(rf/reg-event-db
 :scenarios/edit-change-in-dialog
 (fn [db [_ provider]]
   (-> db
       (assoc-in [:scenarios :open-dialog]      :scenario-changeset)
       (assoc-in [:scenarios :changeset-dialog] {:provider         provider
                                                 :available-budget (compute-available-budget db provider)
                                                 :new-change?      false
                                                 :reset-state?     false}))))

(rf/reg-event-fx
 :scenarios/accept-changeset-dialog
 in-scenarios
 (fn [{:keys [db]} [_]]
   (let [current-scenario (get-in db [:current-scenario])
         updated-provider (get-in db [:changeset-dialog :provider])
         reset-state?     (get-in db [:changeset-dialog :reset-state?])
         new-change?      (nil? (utils/find-by-id (:changeset current-scenario) (:id updated-provider)))
         updated-scenario (update current-scenario
                                  :changeset
                                  (fn [c]
                                    (if new-change?
                                      (conj (vec c) (:change updated-provider))
                                      (utils/replace-by-id c (:change updated-provider)))))]
     {:api (assoc (api/update-scenario (:id current-scenario) updated-scenario)
                  :on-success [:scenarios/update-demand-information])
      :db  (-> db
               (assoc :view-state (if reset-state? :current-scenario (:view-state db)))
               (assoc :current-scenario updated-scenario)
               (assoc :open-dialog nil))})))

(rf/reg-event-fx
 :scenarios/delete-change
 in-scenarios
 (fn [{:keys [db]} [_ id]]
   (let [current-scenario   (:current-scenario db)
         modified-changeset (utils/remove-by-id (:changeset current-scenario) id)
         updated-scenario   (assoc current-scenario :changeset modified-changeset)]
     {:api      (assoc (api/update-scenario (:id current-scenario) updated-scenario)
                       :on-success [:scenarios/update-demand-information])
      :db       (assoc db
                       :current-scenario updated-scenario
                       :changeset-dialog nil)
      :dispatch [:scenarios/cancel-dialog]})))


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
 :scenarios/clear-scenarios
 in-scenarios
 (fn [db [_]]
   (assoc db :list (asdf/new nil))))

(rf/reg-event-db
 :scenarios/change-sort-column-order
 in-scenarios
 (fn [db [_ column order]]
   (assoc db
          :sort-column column
          :sort-order order)))

;; ----------------------------------------------------------------------------

;;; Coverages

(defn- procure-provider-coverage
  [db coverage-id]
  (when (and coverage-id (nil? (get-in db [:coverage-cache coverage-id])))
    (let [scenario-id (get-in db [:current-scenario :id])
          api-request (assoc (api/get-provider-geom scenario-id coverage-id)
                             :on-success [:scenarios/update-coverage-cache coverage-id])]
      {:api api-request})))

(rf/reg-event-db
 :scenarios/update-coverage-cache
 in-scenarios
 (fn [db [_ coverage-id geom]]
   (assoc-in db [:coverage-cache coverage-id] (:coverage-geom geom))))


;;; Providers in map

(rf/reg-event-fx
 :scenarios.map/select-provider
 in-scenarios
 (fn [{:keys [db]} [_ {provider-id :id :as provider}]]
   (when-not (= provider-id (get-in db [:selected-provider :id]))
     (merge {:db (-> db
                     (assoc :selected-provider provider)
                     (assoc :selected-suggestion nil))}
            (procure-provider-coverage db provider-id)))))

(rf/reg-event-db
 :scenarios.map/unselect-provider
 in-scenarios
 (fn [db [_ provider]]
   (if (or (nil? provider) (= (:id provider) (get-in db [:selected-provider :id])))
     (assoc db :selected-provider nil)
     db)))


;;; Suggestions list and selection

(defn- procure-suggestion-coverage
  [db suggestion]
  (if-let [iteration (:iteration suggestion)]
    (when (nil? (get-in db [:suggestions :coverages iteration]))
      (let [scenario-id (get-in db [:current-scenario :id])
            api-request (assoc (api/get-suggestion-geom scenario-id iteration)
                               :on-success [:scenarios/update-suggestion-coverage iteration])]
        {:api api-request}))
    (procure-provider-coverage db (:id suggestion))))

(rf/reg-event-db
 :scenarios/update-suggestion-coverage
 in-scenarios
 (fn [db [_ iteration geom]]
   (assoc-in db [:suggestions :coverages iteration] (:coverage-geom geom))))

(rf/reg-event-fx
 :scenarios.map/select-suggestion
 in-scenarios
 (fn [{:keys [db]} [_ suggestion]]
   (merge {:db (-> db
                   (assoc :selected-suggestion suggestion)
                   (assoc :selected-provider nil))}
          (procure-suggestion-coverage db suggestion))))

(rf/reg-event-db
 :scenarios.map/unselect-suggestion
 in-scenarios
 (fn [db [_ suggestion]]
   (if (= suggestion (:selected-suggestion db))
     (assoc db :selected-suggestion nil)
     db)))

(rf/reg-event-db
 :scenarios/close-suggestions
 in-scenarios
 (fn [db [_ _]]
   (-> db
       (assoc :selected-suggestion nil)
       (assoc-in [:suggestions :locations] nil)
       (assoc-in [:suggestions :improvements] nil)
       (assoc-in [:suggestions :coverages] nil)
       (assoc :view-state :current-scenario))))


;;; Creating new scenario providers

(rf/reg-event-db
 :scenarios.new-action/simple-create-provider
 in-scenarios
 (fn [db [_]]
   (assoc db :view-state :new-provider)))


;;; Suggestions management

(rf/reg-event-fx
 :scenarios.new-action/abort-fetching-suggestions
 in-scenarios
 (fn [{:keys [db]} [_]]
   (when-let [request-key (get-in db [:suggestions :request-key])]
     {:db        (-> db
                     (assoc-in [:suggestions :request-key] nil)
                     (assoc :view-state :current-scenario))
      :api-abort request-key})))

(rf/reg-event-fx
 :scenarios/edit-suggestion
 in-scenarios
 (fn [{:keys [db]} [_ {:keys [location action-capacity coverage] :as suggestion}]]
   (let [{:keys [view-state]} db]
     {:dispatch (if (= view-state :new-provider)
                  [:scenarios/create-provider location {:unsatisfied-demand coverage
                                                        :required-capacity  action-capacity}]
                  [:scenarios/create-change-in-dialog suggestion])})))


;;; Suggestions for new site locations

(def ^:private locations-request-key :locations-request)

(rf/reg-event-fx
 :scenarios.new-provider/fetch-suggested-locations
 in-scenarios
 (fn [{:keys [db]} [_]]
   {:db  (-> db
             (assoc-in [:suggestions :request-key] locations-request-key)
             (assoc :view-state :get-suggestions-for-new-provider))
    :api (assoc (api/suggested-locations-for-new-provider (get-in db [:current-scenario :id]))
                :on-success [:scenarios/suggested-locations]
                :on-failure [:scenarios/no-suggested-locations]
                :key locations-request-key)}))

(rf/reg-event-db
 :scenarios/suggested-locations
 in-scenarios
 (fn [db [_ suggestions]]
   (if (= locations-request-key (get-in db [:suggestions :request-key]))
     (let [suggestions' (map-indexed (fn [index suggestion]
                                       (assoc suggestion :ranked (inc index)))
                                     suggestions)]
       (-> db
           (assoc :view-state :new-provider)
           (assoc-in [:suggestions :locations] suggestions')
           (assoc-in [:suggestions :request-key] nil)))
     db)))

(rf/reg-event-db
 :scenarios/no-suggested-locations
 in-scenarios
 (fn [db [_ {:keys [response]}]]
   (if (= locations-request-key (get-in db [:suggestions :request-key]))
     (do
       (js/alert (or (:error response) "Could not compute suggestions"))
       (-> db
           (assoc :view-state :current-scenario)
           (assoc-in [:suggestions :request-key] nil)))
     db)))


;;; Suggestions for existing sites to improve

(def ^:private improvements-request-key :improvements-request)

(rf/reg-event-fx
 :scenarios.new-action/fetch-suggested-providers-to-improve
 in-scenarios
 (fn [{:keys [db]} [_]]
   {:db  (-> db
             (assoc-in [:suggestions :request-key] improvements-request-key)
             (assoc :view-state :get-suggestions-for-improvements))
    :api (assoc (api/suggested-providers-to-improve (get-in db [:current-scenario :id]))
                :on-success [:scenarios/suggested-interventions]
                :on-failure [:scenarios/no-suggested-interventions]
                :key improvements-request-key)}))

(rf/reg-event-db
 :scenarios/suggested-interventions
 in-scenarios
 (fn [db [_ suggestions]]
   (if (= improvements-request-key (get-in db [:suggestions :request-key]))
     (-> db
         (assoc :view-state :new-intervention)
         (assoc-in [:suggestions :improvements] suggestions)
         (assoc-in [:suggestions :request-key] nil))
     db)))

(rf/reg-event-db
 :scenarios/no-suggested-interventions
 in-scenarios
 (fn [db [_ {:keys [response]}]]
   (if (= improvements-request-key (get-in db [:suggestions :request-key]))
     (do
       (js/alert (or (:error response) "Could not compute suggestions"))
       (-> db
           (assoc :view-state :current-scenario)
           (assoc-in [:suggestions :request-key] nil)))
     db)))


;;; Providers search

(rf/reg-event-fx
 :scenarios/start-searching
 in-scenarios
 (fn [{:keys [db]}]
   {:db       (assoc db :view-state :search-providers)
    :dispatch [:scenarios/search-providers "" nil]}))

(rf/reg-event-fx
 :scenarios/cancel-search
 in-scenarios
 (fn [{:keys [db]}]
   (when (= :search-providers (:view-state db))
     {:db (-> db
              (assoc :view-state :current-scenario)
              (assoc :providers-search nil))})))

(defn- provider-matcher
  [search-value]
  (let [search-value (s/lower-case search-value)]
    (fn [{:keys [name] :as provider}]
      (s/includes? (s/lower-case name) search-value))))

(defn- search-providers
  [db search-value direction]
  (let [last-search-value  (get-in db [:providers-search :search-value])
        last-occurrence    (get-in db [:providers-search :occurrence])
        scenario           (:current-scenario db)
        all-providers      (sort-by :name (db/all-providers scenario))
        matching-providers (if-not (s/blank? search-value)
                             (filterv (provider-matcher search-value) all-providers)
                             all-providers)
        match-count        (count matching-providers)
        occurrence         (if (and (= last-search-value search-value)
                                    (some? last-occurrence))
                             (mod (case direction
                                    :forward  (inc last-occurrence)
                                    :backward (dec last-occurrence)
                                    last-occurrence) match-count)
                             0)
        found-provider     (when (seq matching-providers) (nth matching-providers occurrence))]
    (merge
     {:db (assoc db :providers-search {:search-value search-value
                                       :occurrence   occurrence
                                       :matches      matching-providers})}
     (cond
       (and found-provider (some? direction))
       {:dispatch [:scenarios.map/select-provider (assoc found-provider :hover? true)]}

       (some? direction)
       {:dispatch [:scenarios.map/unselect-provider nil]}))))

(rf/reg-event-fx
 :scenarios/search-providers
 in-scenarios
 (fn [{:keys [db]} [_ search-value direction]]
   (let [direction (#{:forward :backward} direction :forward)]
     (search-providers db search-value direction))))

(rf/reg-event-fx
 :scenarios/refresh-search-providers
 in-scenarios
 (fn [{:keys [db]} [_]]
   (when (= :search-providers (:view-state db))
     (let [last-search-value (get-in db [:providers-search :search-value])]
       (search-providers db last-search-value nil)))))
