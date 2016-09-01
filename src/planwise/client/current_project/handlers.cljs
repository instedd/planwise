(ns planwise.client.current-project.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [clojure.string :refer [split capitalize join]]
            [accountant.core :as accountant]
            [planwise.client.routes :as routes]
            [planwise.client.current-project.api :as api]
            [planwise.client.current-project.db :as db]
            [planwise.client.mapping :as maps]
            [planwise.client.styles :as styles]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by dispatch-delayed]]))

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
       (:facilities :transport) (dispatch [:current-project/load-facilities])
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
     ; We need to issue a full projects list reload here, instead of an
     ; invalidate-projects, in order to prevent a race condition:
     ; 1- User navigates to /project/:id/access/:token
     ; 2- Requests for projects list and request access to shared project are sent to the server
     ; 3- The server processes the projects list before the access request, so the list does not contain the new project
     ; 4- The server processes the request access and sends the response to the client before the response for (3)
     ; 5- The client optimistically adds the new project to the list and asdf/invalidates it
     ; 6- The response for (3) arrives at the client, which updates the list *without* the shared project, and marks the asdf as valid
     ; This should be easily fixed when asdf supports versioning of data, then
     ; the load-projects dispatch can be replaced with:
     ; (dispatch [:projects/invalidate-projects [project-data]])
     (dispatch [:projects/load-projects])
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
                    (initiate-project-update nil))]
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
   ; TODO: Fire a request to update the project shares when opening the dialog to ensure it is up to date
   ; What happens if the user deletes an item from the list before the request returns? In that case,
   ; the item that was just deleted will re-appear.
   (assoc db :view-state :share-dialog)))

(register-handler
 :current-project/close-share-dialog
 in-current-project
 (fn [db [_]]
   (assoc db :view-state :project)))

(register-handler
 :current-project/reset-share-token
 in-current-project
 (fn [db [_]]
   (let [id (db/project-id db)]
     (api/reset-share-token id :current-project/share-token-loaded)
     (assoc-in db [:sharing :token-state] :reloading))))

(register-handler
 :current-project/share-token-loaded
 in-current-project
 (fn [db [_ {token :token}]]
   (-> db
     (assoc-in [:project-data :share-token] token)
     (assoc-in [:sharing :token-state] nil))))

(defn- remove-project-share
  [coll user-id]
  (remove #(= user-id (:user-id %)) coll))

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
   ; TODO: Should we reload the list if accessed when invalidated?
   ; If we do so, we risk loading a stale list if:
   ; 1- The user an item from the list
   ; 2- We optimistically remove it and issue the delete request
   ; 3- The request returns from the server and we invalidate the list
   ; 4- The invalidate triggers a full reload of the list
   ; 5- The user deletes another item, which is optimistically deleted
   ; 6- The full reload of the list resolves, adding the item just deleted by the user
   ; Should we cancel the previous request to reload the shares list?
   ; Or simply ignore the invalid status of the list?
   (update db :shares asdf/invalidate! remove-by :user-id user-id)))

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
