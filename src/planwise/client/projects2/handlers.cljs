(ns planwise.client.projects2.handlers
  (:require [re-frame.core :refer [register-handler subscribe] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.projects2.api :as api]
            [planwise.client.routes :as routes]
            [planwise.client.effects :as effects]
            [planwise.client.projects2.db :as db]))

(def in-projects2 (rf/path [:projects2]))

;;------------------------------------------------------------------------------
;; Creating New Project

(rf/reg-event-fx
  :projects2/new-project
  in-projects2
  (fn [_ [_ ]]
   {:api (assoc (api/create-project!)
                :on-success [:projects2/project-created])}))

(rf/reg-event-fx
  :projects2/project-created
  in-projects2
  (fn [{:keys [db]} [_ project-id]]
      {:db        (assoc db :current-project nil)  
       :navigate  (routes/projects2-show project-id)}))

;;------------------------------------------------------------------------------
;; Updating Project

(rf/reg-event-db
  :projects2/save-project-data
  in-projects2
  (fn [db [_ current-project]]
    (assoc db :current-project current-project)))

(rf/reg-event-fx
  :projects2/project-not-found
  (fn [_ _]
    {:navigate (routes/projects2)}))

(rf/reg-event-fx
  :projects2/get-project-data
  in-projects2
  (fn [{:keys [db]} [_ id]]
    {:api (assoc (api/get-project id)
                 :on-success [:projects2/save-project-data]
                 :on-failure [:projects2/project-not-found])}))

;;------------------------------------------------------------------------------
;; Debounce

(rf/reg-event-fx 
  :projects2/save-data
  (fn [{:keys [db]} [_ key value]]
    (let [id    (get-in db [:projects2 :current-project :id])]
      {:db                        (assoc-in db [:projects2 :current-project key] value)
       :dispatch-debounce         [{:id (str :projects2/save id)
                                    :timeout 250
                                    :action :dispatch
                                    :event [:projects2/persist-data]}]})))

(rf/reg-event-fx 
  :projects2/persist-data
  in-projects2 
  (fn [{:keys [db]} _] 
    (let [current-project   (rf/subscribe [:projects2/current-project]) 
          id                (:id @current-project) 
          name              (:name @current-project)] 
      {:api       (api/update-project id name)})))
  