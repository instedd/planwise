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
   (let [project-id    (get-in db [:page-params :project-id])]
     {:navigate (routes/scenarios {:project-id project-id :id id})})))

(rf/reg-event-fx
 :scenarios/get-scenario
 (fn [_ [_ id]]
   {:api (assoc (api/load-scenario id)
                :on-success [:scenarios/save-current-scenario]
                :on-failure [:scenarios/scenario-not-found])}))

(rf/reg-event-fx
 :scenarios/copy-scenario
 in-scenarios
 (fn [{:keys [db]} [_ id]]
   {:api  (assoc (api/copy-scenario id)
                 :on-success [:scenarios/load-scenario])}))

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
     {:api  (api/update-scenario (:id current-scenario) current-scenario)
      :db   (-> db
                ;; do not reset rename-dialog to nil or dialog animation after <enter> will fail
                (assoc-in [:current-scenario :name] name)
                (assoc-in [:view-state] :current-scenario))})))

;;Creating new-sites

(rf/reg-event-db
 :scenarios/adding-new-site
 in-scenarios
 (fn [db [_]]
   (assoc db :view-state :new-site)))

(rf/reg-event-fx
 :scenarios/create-site
 in-scenarios
 (fn [{:keys [db]} [_ {:keys [lat lon]}]]
   (let [{:keys [current-scenario]} db
         new-site  (assoc db/initial-site :location {:lat lat :lon lon})
         current-scenario (update current-scenario  :changeset #(conj % new-site))]
     {:api  (api/update-scenario (:id current-scenario) current-scenario)
      :db   (-> db
                (update-in [:current-scenario :changeset] #(conj % new-site))
                (assoc-in [:view-state] :current-scenario))})))

(rf/reg-event-db
 :scenarios/open-site-dialog
 in-scenarios
 (fn [db [_ site]]
   (assoc db
          :view-state :edit-site
          :edit-site site)))

(rf/reg-event-db
 :scenarios/accept-site-dialog
 in-scenarios
 (fn [{:keys [site]} [_]]
   (let [current-scenario (update current-scenario  :changeset #(conj % site))]
     {:api  (api/update-scenario (:id current-scenario) current-scenario)
      :db   (-> db
                (update-in [:current-scenario :changeset] #(conj % site))
                (assoc-in [:view-state] :current-scenario))})))

(rf/reg-event-db
 :scenarios/cancel-site-dialog
 in-scenarios
 (fn [db [_]]
   (assoc db
          :view-state :current-scenario
          :edit-site nil)))