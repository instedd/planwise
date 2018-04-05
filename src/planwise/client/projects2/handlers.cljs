(ns planwise.client.projects2.handlers
  (:require [re-frame.core :refer [register-handler subscribe] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.projects2.api :as api]
            [planwise.client.routes :as routes]
            [planwise.client.effects :as effects]
            [planwise.client.projects2.db :as db]
            [planwise.client.utils :as utils]))

(def in-projects2 (rf/path [:projects2]))

;;------------------------------------------------------------------------------
;; Creating New Project

(rf/reg-event-fx
 :projects2/new-project
 in-projects2
 (fn [_ [_]]
   {:api (assoc (api/create-project!)
                :on-success [:projects2/project-created])}))

(rf/reg-event-fx
 :projects2/project-created
 in-projects2
 (fn [{:keys [db]} [_ project]]
   (let [project-id   (:id project)
         project-item (select-keys project [:id :name :state])
         new-list     (cons project-item (:list db))]
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
   (-> db
       (assoc :current-project current-project)
    ;; Keep list in sync with current project
       (update :list
               (fn [list]
                 (utils/update-by-id list (:id current-project)
                                     #(-> %
                                          (assoc :state (:state current-project))
                                          (assoc :name (:name current-project))
                                          (assoc :region-id (:region-id current-project)))))))))


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


(rf/reg-event-fx
 :projects2/start-project
 in-projects2
 (fn [{:keys [db]} [_ id]]
   {:api (assoc (api/start-project! id)
                :on-success [:projects2/save-project-data]
                :on-failure [:projects2/project-not-found])}))

;;------------------------------------------------------------------------------
;; Debounce-updating project

(rf/reg-event-fx
 :projects2/save-key
 in-projects2
 (fn [{:keys [db]} [_ path data]]
   (let [{:keys [list current-project]} db
         {:keys [id name]}              current-project
         path                           (if (vector? path) path [path])
         current-project-path           (into [:current-project] path)]
     {:db                (-> db
                             (assoc-in current-project-path data))
      :dispatch-debounce [{:id (str :projects2/save id)
                           :timeout 250
                           :action :dispatch
                           :event [:projects2/persist-current-project]}]})))

(rf/reg-event-fx
 :projects2/persist-current-project
 in-projects2
 (fn [{:keys [db]} [_]]
   (let [current-project   (:current-project db)
         id                (:id current-project)]
     {:api         (assoc (api/update-project id current-project)
                          :on-success [:projects2/update-current-project-from-server])})))

(rf/reg-event-fx
 :projects2/update-current-project-from-server
 in-projects2
 (fn [{:keys [db]} [_ project]]
   (let [current-project (:current-project db)]
     (if (= (:id project) (:id current-project))
        ;; keep current values of current-project except the once that could be updated from server
       (let [updated-project (-> current-project
                                 (assoc :coverage-algorithm (:coverage-algorithm project)))]
         {:dispatch [:projects2/save-project-data updated-project]})))))


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
