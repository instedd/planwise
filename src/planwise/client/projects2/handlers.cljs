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
    (let [project-id          (:id project)
          {:keys [id name]}   project
          new-list            (cons {:id id :name name} (:list db))]
      {:db        (-> db
                    (assoc :current-project nil)
                    (assoc :list new-list))
       :navigate  (routes/projects2-show {:id project-id})})))

;;------------------------------------------------------------------------------
;; Updating db

(rf/reg-event-db
  :projects2/save-project-data
  in-projects2
  (fn [db [_ current-project]]
    (assoc db :current-project current-project)))

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

(defn- new-list 
  [list current-project id key data]
  (let [projects-update         (remove (fn [project] (= (:id project) id)) list)
        updated-project         (assoc-in current-project key data)]
      (cons updated-project projects-update)))

(rf/reg-event-fx
  :projects2/save-key
  in-projects2
  (fn [{:keys [db]} [_ key data]]
    (let [{:keys [list current-project]}   db
          {:keys [id name]}                current-project
          new-key                   (if (vector? key) key [key])
          current-project-path      (into [] (cons :current-project new-key))
          new-list                  (if (= key :name) (new-list list {:id id :name name} id new-key data))]
      {:db                        (-> db
                                   (assoc-in current-project-path data)
                                   (assoc :list new-list))

       :dispatch-debounce         [{:id (str :projects2/save id)
                                    :timeout 250
                                    :action :dispatch
                                    :event [:projects2/persist-current-project]}]})))
 

(rf/reg-event-fx
  :projects2/persist-current-project
  in-projects2
  (fn [{:keys [db]} [_]]
    (let [current-project   (:current-project db)
          id                (:id current-project)]
      {:api          (api/update-project id current-project)})))

;;------------------------------------------------------------------------------
;; Listing projects

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
