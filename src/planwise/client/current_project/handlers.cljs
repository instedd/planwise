(ns planwise.client.current-project.handlers
  (:require [re-frame.core :as rf]
            [clojure.string :refer [split capitalize join]]
            [planwise.client.routes :as routes]
            [planwise.client.current-project.api :as api]
            [planwise.client.datasets.api :as datasets-api]
            [planwise.client.current-project.db :as db]
            [planwise.client.datasets.db :as datasets-db]
            [planwise.client.mapping :as maps]
            [planwise.client.styles :as styles]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by]]
            [planwise.client.datasets.db :refer [dataset->status]]))

(def in-current-project (rf/path [:current-project]))

(def request-delay 500)
(def loading-hint-delay 1000)

;; ---------------------------------------------------------------------------
;; Facility types handlers

(rf/reg-event-fx
 :current-project/fetch-facility-types
 in-current-project
 (fn [{:keys [db]} [_]]
   (let [dataset-id (db/dataset-id db)]
     {:api (assoc (api/fetch-facility-types dataset-id)
                  :on-success [:current-project/facility-types-received])})))

(defn assign-facility-colours
  ([items]
   (assign-facility-colours items styles/facility-types-palette))
  ([items colours]
   (map (fn [item colour] (assoc item :colour colour))
        items (cycle colours))))

(rf/reg-event-db
 :current-project/facility-types-received
 in-current-project
 (fn [db [_ types]]
   (let [types-with-colours (->> types
                                 (sort-by :value)
                                 assign-facility-colours)]
     (assoc-in db [:filter-definitions :facility-type] types-with-colours))))

;; ---------------------------------------------------------------------------
;; Loading a project

(defn section->with
  [section]
  (case section
    :demographics nil
    :facilities   :facilities
    :transport    :facilities-with-demand))

(defn- visit-tab
  [wizard-state visiting-tab]
  (let [new-tabs (into {}
                       (map (fn [[tab state]]
                              (if (= tab visiting-tab)
                                (case state
                                  :visited [tab :visited]
                                  [tab :visiting])
                                (case state
                                  (:visiting, :visited) [tab :visited]
                                  [tab :unvisited])))
                            (:tabs wizard-state)))]
    (assoc wizard-state :tabs new-tabs)))

(defn- update-wizard-state
  [db next-tab]
  (let [state   (:wizard db)
        active? (:set state)]
    (when active?
      (let [project-id (db/project-id db)
            new-state  (visit-tab state next-tab)]
        (merge {:db (assoc db :wizard new-state)}
               (when (not= state new-state)
                 {:api (api/update-project-state project-id new-state)}))))))

(rf/reg-event-fx
 :current-project/tab-visited
 in-current-project
 (fn [{:keys [db]} [_ section]]
   (update-wizard-state db section)))

(rf/reg-event-fx
 :current-project/navigate-project
 in-current-project
 (fn [{:keys [db]} [_ project-id section]]
   (let [event-key (if (not= project-id (db/project-id db))
                     :current-project/load-project
                     :current-project/reload-project)]
     {:dispatch [event-key project-id (section->with section)]})))

(rf/reg-event-fx
 :current-project/load-project
 in-current-project
 (fn [{:keys [db]} [_ project-id with-data]]
   {:api (assoc (api/load-project project-id with-data)
                :on-success [:current-project/project-loaded]
                :on-failure [:current-project/not-found])
    :db  db/initial-db}))

(rf/reg-event-fx
 :current-project/reload-project
 in-current-project
 (fn [_ [_ project-id with-data]]
   {:api (assoc (api/load-project project-id with-data)
                :on-success [:current-project/project-updated])}))

(rf/reg-event-fx
 :current-project/access-project
 in-current-project
 (fn [_ [_ project-id token]]
   {:api (assoc (api/access-project project-id token (section->with :demographics))
                :on-success [:current-project/project-access-granted]
                :on-failure [:current-project/not-found])
    :db  db/initial-db}))

(defn- project-loaded
  [db project-data]
  {:dispatch-n [[:current-project/fetch-facility-types]
                [:regions/load-regions-with-geo [(:region-id project-data)]]]
   :db         (db/new-viewmodel project-data)})


(rf/reg-event-fx
 :current-project/project-access-granted
 in-current-project
 (fn [{:keys [db]} [_ project-data]]
   (merge (project-loaded db project-data)
          {:dispatch [:projects/invalidate-projects [project-data]]
           :navigate (routes/project-demographics project-data)})))

(rf/reg-event-fx
 :current-project/project-loaded
 in-current-project
  (fn [{:keys [db]} [_ project-data]]
    (project-loaded db project-data)))

(rf/reg-event-fx
 :current-project/not-found
 (fn [_ _]
   {:navigate (routes/home)}))

(rf/reg-event-db
 :current-project/isochrones-loaded
 in-current-project
 (fn [db [_ {:keys [map-key unsatisfied-count facilities threshold simplify], :as response}]]
   (let [level (maps/simplify->geojson-level simplify)
         isochrones  (->> facilities
                          (filter #(some? (:isochrone %)))
                          (map (juxt :id (comp js/JSON.parse :isochrone)))
                          (flatten)
                          (apply hash-map))]
     (update-in db [:facilities :isochrones threshold level] #(merge % isochrones)))))

;; ----------------------------------------------------------------------------
;; Current project dataset

(rf/reg-event-fx
 :current-project/load-dataset
 in-current-project
 (fn [{:keys [db]} _]
   {:api (assoc (datasets-api/load-dataset (db/dataset-id db))
                :on-success [:current-project/dataset-loaded])
    :db  (update db :dataset asdf/reload!)}))

(rf/reg-event-db
 :current-project/invalidate-dataset
 in-current-project
 (fn [db _]
   (update db :dataset asdf/invalidate!)))

(rf/reg-event-fx
 :current-project/dataset-loaded
 in-current-project
 (fn [{:keys [db]} [_ dataset]]
   (merge {:db (update db :dataset asdf/reset! dataset)}
          (when (#{:importing :unknown} (datasets-db/dataset->status dataset))
            {:dispatch-later [{:ms 5000 :dispatch [:current-project/invalidate-dataset]}]}))))


;; ---------------------------------------------------------------------------
;; Project filter updating

(def timeout-key [:current-project :map-state :timeout])
(def request-key [:current-project :map-state :request])

(def set-request-timeout
  {:delayed-dispatch {:ms       request-delay
                      :key      timeout-key
                      :dispatch [:current-project/trigger-map-request]}})

(def cancel-prev-timeout
  {:cancel-dispatch timeout-key})

(def cancel-prev-request
  {:api-abort request-key})

(rf/reg-event-db
 :current-project/show-map-loading-hint
 in-current-project
 (fn [db _]
   (if (= (get-in db [:map-state :current]) :loading)
     (assoc-in db [:map-state :current] :loading-displayed)
     db)))

(rf/reg-event-fx
 :current-project/trigger-map-request
 in-current-project
 (fn [{:keys [db]} _]
   (let [filters      (db/project-filters db)
         project-id   (get-in db [:project-data :id])
         request-with (get-in db [:map-state :request-with])]
     {:api (assoc (api/update-project project-id filters request-with)
                  :on-success [:current-project/project-updated]
                  :key request-key)
      :delayed-dispatch {:ms       loading-hint-delay
                         :key      timeout-key
                         :dispatch [:current-project/show-map-loading-hint]}
      :db (assoc-in db [:map-state :current] :loading)})))

(defn initiate-project-update
  "Effects necessary to initiate a project update given current state"
  [db request-with]
  (assoc (case (get-in db [:map-state :current])
           (:loaded  :request-pending)   set-request-timeout
           (:loading :loading-displayed) (merge set-request-timeout cancel-prev-request))
         :db (-> db
                 (assoc-in [:map-state :current] :request-pending)
                 (assoc-in [:map-state :request-with] request-with))))

(rf/reg-event-fx
 :current-project/toggle-filter
 in-current-project
 (fn [{:keys [db]} [_ filter-group filter-key filter-value]]
   (let [path [:project-data :filters filter-group filter-key]
         current-filter (set (get-in db path))
         toggled-filter (if (contains? current-filter filter-value)
                          (disj current-filter filter-value)
                          (conj current-filter filter-value))
         new-db (-> db
                    (assoc-in path (vec toggled-filter))
                    (assoc-in [:facilities :isochrones] nil))
         project-id (get-in db [:project-data :id])]
     (initiate-project-update new-db :facilities))))

(rf/reg-event-fx
 :current-project/set-transport-time
 in-current-project
 (fn [{:keys [db]} [_ time]]
   (let [new-db (-> db
                    (assoc-in [:project-data :filters :transport :time] time)
                    (update :map-key asdf/reload!))]
     (initiate-project-update new-db :demand))))

(rf/reg-event-fx
 :current-project/project-updated
 in-current-project
 (fn [{:keys [db]} [_ project]]
   (assoc cancel-prev-timeout
          :dispatch [:projects/invalidate-projects]
          :db       (-> db
                        (assoc-in [:map-state :current] :loaded)
                        (db/update-viewmodel project)))))

;; ----------------------------------------------------------------------------
;; Project deletion

(rf/reg-event-fx
 :current-project/delete-project
 in-current-project
 (fn [{:keys [db]} [_]]
   (let [id (db/project-id db)]
     {:dispatch [:projects/delete-project id]
      :navigate (routes/home)
      ;; clear the current project view model
      :db db/initial-db})))

(rf/reg-event-fx
 :current-project/leave-project
 in-current-project
 (fn [{:keys [db]} [_]]
   (let [id (db/project-id db)]
     {:dispatch [:projects/leave-project id]
      :navigate (routes/home)
      ;; clear the current project view model
      :db db/initial-db})))

;; ---------------------------------------------------------------------------
;; Project map view handlers

(rf/reg-event-db
 :current-project/update-position
 in-current-project
 (fn [db [_ new-position]]
   (assoc-in db [:map-view :position] new-position)))

(rf/reg-event-db
 :current-project/update-zoom
 in-current-project
 (fn [db [_ new-zoom]]
   (assoc-in db [:map-view :zoom] new-zoom)))

;; ---------------------------------------------------------------------------
;; Sharing handlers

(rf/reg-event-db
 :current-project/open-share-dialog
 in-current-project
 (fn [db [_]]
   ; Invalidate the list of shares when opening the share dialog to force a reload
   (-> db
     (assoc :view-state :share-dialog)
     (update :shares asdf/invalidate!))))

(rf/reg-event-db
 :current-project/close-share-dialog
 in-current-project
 (fn [db [_]]
   (assoc db :view-state :project)))

(rf/reg-event-fx
 :current-project/load-project-shares
 in-current-project
 (fn [{:keys [db]} [_]]
   {:api (assoc (api/load-project (db/project-id db) nil)
                :on-success [:current-project/project-shares-loaded]
                :mapper-fn :shares)
    :db  (update db :shares asdf/reload!)}))

(rf/reg-event-db
 :current-project/project-shares-loaded
 in-current-project
 (fn [db [_ data]]
   (update db :shares asdf/reset! data)))

(rf/reg-event-fx
 :current-project/reset-share-token
 in-current-project
 (fn [{:keys [db]} [_]]
   {:api (assoc (api/reset-share-token (db/project-id db))
                :on-success [:current-project/share-token-loaded])
    :db  (update-in db [:sharing :token] asdf/reload!)}))

(rf/reg-event-db
 :current-project/share-token-loaded
 in-current-project
 (fn [db [_ {token :token}]]
   (update-in db [:sharing :token] asdf/reset! token)))

(rf/reg-event-db
 :current-project/search-shares
 in-current-project
 (fn [db [_ string]]
   (assoc-in db [:sharing :shares-search-string] string)))

(rf/reg-event-fx
 :current-project/delete-share
 in-current-project
 (fn [{:keys [db]} [_ user-id]]
   {:api (assoc (api/delete-share (db/project-id db) user-id)
                :on-success [:current-project/share-deleted])
    :db  (update db :shares asdf/swap! remove-by :user-id user-id)}))

(rf/reg-event-db
 :current-project/share-deleted
 in-current-project
 (fn [db [_ {:keys [user-id project-id]}]]
   ; Optimistic update (??)
   (update db :shares asdf/swap! remove-by :user-id user-id)))

(rf/reg-event-db
 :current-project/reset-sharing-emails-text
 in-current-project
 (fn [db [_ text]]
   (assoc-in db [:sharing :emails-text] text)))

(rf/reg-event-fx
 :current-project/send-sharing-emails
 in-current-project
 (fn [{:keys [db]} [_ text]]
   (let [emails (db/split-emails (get-in db [:sharing :emails-text]))]
     {:api (assoc (api/send-sharing-emails (db/project-id db) emails)
                  :on-success [:current-project/sharing-emails-sent])
      :db  (assoc-in db [:sharing :state] :sending)})))

(rf/reg-event-fx
 :current-project/sharing-emails-sent
 in-current-project
 (fn [{:keys [db]} [_ text]]
   {:dispatch-later [{:ms 3000 :dispatch [:current-project/clear-sharing-emails-state]}]
    :db (-> db
            (assoc-in [:sharing :emails-text] "")
            (assoc-in [:sharing :state] :sent))}))

(rf/reg-event-db
 :current-project/clear-sharing-emails-state
 in-current-project
 (fn [db _]
   (assoc-in db [:sharing :state] nil)))
