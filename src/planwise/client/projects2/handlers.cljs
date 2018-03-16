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
  (fn [{:keys [db]} [_ project]]
    (let [project-id    (:id project)]
      {:db        (do (assoc db :current-project nil)
                      (assoc db :list (cons project (:list db))))
       :navigate  (routes/projects2-show {:id project-id})})))

;;------------------------------------------------------------------------------
;; Updating db

(rf/reg-event-db
  :projects2/save-project-data
  (fn [db [_ current-project]]
    (assoc-in db [:projects2 :current-project] current-project)))

(rf/reg-event-fx
  :projects2/project-not-found
  in-projects2
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
;; Debounce-updating project

(rf/reg-event-fx 
  :projects2/save-data
  in-projects2
  (fn [{:keys [db]} [_ key value]]
    (let [id    (get-in db [:current-project :id])]
      {:db                        (assoc-in db [:current-project key] value)
       :dispatch-debounce         [{:id (str :projects2/save id)
                                    :timeout 250
                                    :action :dispatch
                                    :event [:projects2/persist-data]}]})))

(rf/reg-event-fx 
  :projects2/persist-data
  in-projects2 
  (fn [{:keys [db]} _] 
    (let [current-project   (:current-project db) 
          id                (:id current-project) 
          name              (:name current-project)] 
      {:api  (assoc (api/update-project id name)
                    :on-success [:projects2/save-in-list id])})))
                    

;;------------------------------------------------------------------------------
;; Listing projects

(rf/reg-event-db
  :projects2/save-in-list
  in-projects2
  (fn [db [_ id]]
    (let [projects                (:list db)
          current-project         (:current-project db)
          projects-update         (remove (fn [project] (= (:id project) id)) projects)]
        (assoc db :list (cons current-project projects-update)))))


(rf/reg-event-fx 
  :projects2/projects-list
  in-projects2 
  (fn [{:keys [db]} _] 
    {:api (assoc (api/list-projects)
          :on-success [:projects2/projects-listed])}))

(rf/reg-event-db
  :projects2/projects-listed
  in-projects2
  (fn [db [_ projects-list]]
    (assoc db :list projects-list)))
