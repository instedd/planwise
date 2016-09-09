(ns planwise.client.current-project.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [clojure.string :refer [split capitalize join]]
            [accountant.core :as accountant]
            [planwise.client.routes :as routes]
            [planwise.client.current-project.api :as api]
            [planwise.client.datasets.api :as datasets-api]
            [planwise.client.current-project.db :as db]
            [planwise.client.datasets.db :as datasets-db]
            [planwise.client.mapping :as maps]
            [planwise.client.styles :as styles]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by dispatch-delayed]]
            [planwise.client.datasets.db :refer [dataset->status]]))

(def in-current-project (path [:current-project]))

(def request-delay 500)
(def loading-hint-delay 2500)

;; ---------------------------------------------------------------------------
;; Facility types handlers

(register-handler
 :current-project/fetch-facility-types
 in-current-project
 (fn [db [_]]
   (let [dataset-id (db/dataset-id db)]
     (api/fetch-facility-types dataset-id :current-project/facility-types-received))
   db))

(register-handler
 :current-project/facility-types-received
 in-current-project
 (fn [db [_ types]]
   (let [types-with-colours (map (fn [type colour]
                                   (assoc type :colour colour))
                                 (sort-by :value types)
                                 (cycle styles/facility-types-palette))]
     (assoc-in db [:filter-definitions :facility-type] types-with-colours))))

;; ---------------------------------------------------------------------------
;; Loading a project

(defn section->with
  [section]
  (case section
    :demographics nil
    :facilities :facilities
    :transport :facilities-with-demand))

(defn- project-loaded [db project-data]
  (dispatch [:current-project/fetch-facility-types])
  (dispatch [:regions/load-regions-with-geo [(:region-id project-data)]])
  (db/new-viewmodel project-data))

(register-handler
 :current-project/navigate-project
 in-current-project
 (fn [db [_ project-id section]]
   (if (not= project-id (db/project-id db))
     (dispatch [:current-project/load-project project-id (section->with section)])
     (case section
       (:facilities :transport) (dispatch [:current-project/load-facilities]) ; TODO: Get project and extract required data, remove otherwise unused endpoint
       nil))
   db))

(register-handler
 :current-project/load-project
 in-current-project
 (fn [db [_ project-id with-data]]
   (api/load-project project-id with-data
                     :current-project/project-loaded :current-project/not-found)
   db/initial-db))

(register-handler
 :current-project/access-project
 in-current-project
 (fn [db [_ project-id token]]
   (api/access-project project-id token (section->with :demographics)
                       :current-project/project-access-granted :current-project/not-found)
   db/initial-db))

(register-handler
 :current-project/project-access-granted
 in-current-project
 (fn [db [_ project-data]]
   (let [db (project-loaded db project-data)]
     (dispatch [:projects/invalidate-projects [project-data]])
     (accountant/navigate! (routes/project-demographics project-data))
     db)))

(register-handler
 :current-project/not-found
 in-current-project
 (fn [db [_]]
   (accountant/navigate! (routes/home))
   db))

(register-handler
 :current-project/project-loaded
 in-current-project
  (fn [db [_ project-data]]
    (project-loaded db project-data)))

(register-handler
 :current-project/load-facilities
 in-current-project
 (fn [db [_ force?]]
   (when (or force? (nil? (get-in db [:facilities :list])))
     (api/fetch-facilities (db/facilities-criteria db) :current-project/facilities-loaded))
   db))

(register-handler
 :current-project/facilities-loaded
 in-current-project
 (fn [db [_ response]]
   (assoc-in db [:facilities :list] (:facilities response))))

(register-handler
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

(register-handler
 :current-project/load-dataset
 in-current-project
 (fn [db _]
   (datasets-api/load-dataset (db/dataset-id db) :current-project/dataset-loaded)
   (update db :dataset asdf/reload!)))

(register-handler
 :current-project/invalidate-dataset
 in-current-project
 (fn [db _]
   (update db :dataset asdf/invalidate!)))

(register-handler
 :current-project/dataset-loaded
 in-current-project
 (fn [db [_ dataset]]
   (when (#{:importing :unknown} (datasets-db/dataset->status dataset))
     (dispatch-delayed 5000 [:current-project/invalidate-dataset]))
   (update db :dataset asdf/reset! dataset)))


;; ---------------------------------------------------------------------------
;; Project filter updating

(defn set-request-timeout [db]
  (assoc-in db [:map-state :timeout]
            (js/setTimeout #(dispatch [:current-project/trigger-map-request]) request-delay)))

(defn cancel-prev-timeout [db]
  (update-in db [:map-state :timeout] js/clearTimeout))

(defn cancel-prev-request [db]
  (some-> (get-in db [:map-state :request]) ajax.protocols/-abort)
  (assoc-in db [:map-state :request] nil))

(register-handler
 :current-project/show-map-loading-hint
 in-current-project
 (fn [db _]
   (if (= (get-in db [:map-state :current]) :loading)
     (assoc-in db [:map-state :current] :loading-displayed)
     db)))

(register-handler
 :current-project/trigger-map-request
 in-current-project
 (fn [db _]
   (let [filters (db/project-filters db)
         project-id (get-in db [:project-data :id])
         request-with (get-in db [:map-state :request-with])]
     (-> db
         (assoc-in [:map-state :request] (api/update-project project-id filters request-with :current-project/project-updated))
         (assoc-in [:map-state :timeout] (js/setTimeout #(dispatch [:current-project/show-map-loading-hint]) loading-hint-delay))
         (assoc-in [:map-state :current] :loading)))))

(defn initiate-project-update [db request-with]
  (let [new-db
        (case (get-in db [:map-state :current])
          :loaded            (set-request-timeout db)
          :request-pending   (-> db
                                 cancel-prev-timeout
                                 set-request-timeout)
          :loading           (-> db
                                 cancel-prev-timeout
                                 cancel-prev-request
                                 set-request-timeout)
          :loading-displayed (-> db
                                 cancel-prev-request
                                 set-request-timeout))]
    (-> new-db
        (assoc-in [:map-state :current] :request-pending)
        (assoc-in [:map-state :request-with] request-with))))

(register-handler
 :current-project/toggle-filter
 in-current-project
 (fn [db [_ filter-group filter-key filter-value]]
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

(register-handler
 :current-project/set-transport-time
 in-current-project
 (fn [db [_ time]]
   (let [new-db (-> db
                    (assoc-in [:project-data :filters :transport :time] time)
                    (initiate-project-update :demand))]
     new-db)))

(register-handler
 :current-project/project-updated
 in-current-project
 (fn [db [_ project]]
   (let [prev-state (get-in db [:map-state :current])
         new-db (-> db
                    cancel-prev-timeout
                    (assoc-in [:map-state :current] :loaded))]
     (dispatch [:projects/invalidate-projects])
     (db/update-viewmodel new-db project))))

;; ----------------------------------------------------------------------------
;; Project deletion

(register-handler
 :current-project/delete-project
 in-current-project
 (fn [db [_]]
   (let [id (db/project-id db)]
     (dispatch [:projects/delete-project id]))
   (accountant/navigate! (routes/home))
   ;; clear the current project view model
   db/initial-db))

(register-handler
 :current-project/leave-project
 in-current-project
 (fn [db [_]]
   (let [id (db/project-id db)]
     (dispatch [:projects/leave-project id]))
   (accountant/navigate! (routes/home))
   ;; clear the current project view model
   db/initial-db))

;; ---------------------------------------------------------------------------
;; Project map view handlers

(register-handler
 :current-project/update-position
 in-current-project
 (fn [db [_ new-position]]
   (assoc-in db [:map-view :position] new-position)))

(register-handler
 :current-project/update-zoom
 in-current-project
 (fn [db [_ new-zoom]]
   (assoc-in db [:map-view :zoom] new-zoom)))

;; ---------------------------------------------------------------------------
;; Sharing handlers

(register-handler
 :current-project/open-share-dialog
 in-current-project
 (fn [db [_]]
   ; Invalidate the list of shares when opening the share dialog to force a reload
   (-> db
     (assoc :view-state :share-dialog)
     (update :shares asdf/invalidate!))))

(register-handler
 :current-project/close-share-dialog
 in-current-project
 (fn [db [_]]
   (assoc db :view-state :project)))

(register-handler
 :current-project/load-project-shares
 in-current-project
 (fn [db [_]]
   (api/load-project (db/project-id db) nil #(dispatch [:current-project/project-shares-loaded (:shares %)]))
   (update db :shares asdf/reload!)))

(register-handler
 :current-project/project-shares-loaded
 in-current-project
 (fn [db [_ data]]
   (update db :shares asdf/reset! data)))

(register-handler
 :current-project/reset-share-token
 in-current-project
 (fn [db [_]]
   (let [id (db/project-id db)]
     (api/reset-share-token id :current-project/share-token-loaded)
     (update-in db [:sharing :token] asdf/reload!))))

(register-handler
 :current-project/share-token-loaded
 in-current-project
 (fn [db [_ {token :token}]]
   (update-in db [:sharing :token] asdf/reset! token)))

(register-handler
 :current-project/search-shares
 in-current-project
 (fn [db [_ string]]
   (assoc-in db [:sharing :shares-search-string] string)))

(register-handler
 :current-project/delete-share
 in-current-project
 (fn [db [_ user-id]]
   (let [project-id (db/project-id db)]
     (api/delete-share project-id user-id :current-project/share-deleted)
     (update db :shares asdf/swap! remove-by :user-id user-id))))

(register-handler
 :current-project/share-deleted
 in-current-project
 (fn [db [_ {:keys [user-id project-id]}]]
   ; Optimistic update
   (update db :shares asdf/swap! remove-by :user-id user-id)))

(register-handler
 :current-project/reset-sharing-emails-text
 in-current-project
 (fn [db [_ text]]
   (assoc-in db [:sharing :emails-text] text)))

(register-handler
 :current-project/send-sharing-emails
 in-current-project
 (fn [db [_ text]]
   (let [emails (db/split-emails (get-in db [:sharing :emails-text]))]
     (api/send-sharing-emails (db/project-id db) emails :current-project/sharing-emails-sent))
   (-> db
     (assoc-in [:sharing :state] :sending))))

(register-handler
 :current-project/sharing-emails-sent
 in-current-project
 (fn [db [_ text]]
   (dispatch-delayed 3000 [:current-project/clear-sharing-emails-state])
   (-> db
     (assoc-in [:sharing :emails-text] "")
     (assoc-in [:sharing :state] :sent))))

(register-handler
 :current-project/clear-sharing-emails-state
 in-current-project
 (fn [db _]
   (assoc-in db [:sharing :state] nil)))
