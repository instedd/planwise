(ns planwise.client.projects2.handlers
  (:require [re-frame.core :refer [register-handler subscribe] :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.projects2.api :as api]
            [planwise.client.routes :as routes]
            [planwise.client.effects :as effects]
            [planwise.client.projects2.db :as db]
            [planwise.client.utils :as utils]
            [clojure.spec.alpha :as s]))


(def in-projects2 (rf/path [:projects2]))

;;------------------------------------------------------------------------------
;; Creating New Project

(rf/reg-event-fx
 :projects2/new-project
 in-projects2
 (fn [_ [_ defaults]]
   {:api (assoc (api/create-project! defaults)
                :on-success [:projects2/project-created])}))

(rf/reg-event-fx
 :projects2/template-project
 in-projects2
 (fn [_ [_]]
   {:navigate (routes/projects2-new {})}))

(rf/reg-event-fx
 :projects2/get-templates-list
 in-projects2
 (fn [_ [_]]
   {:api (assoc (api/list-templates) :on-success [:projects2/templates-fetched])}))
    ; {:api (assoc (api/create-project!)
    ;              :on-success [:projects2/project-created])}))
(rf/reg-event-fx
 :projects2/templates-fetched
 in-projects2
 (fn [{:keys [db]} [_ templates]]
   {:db     (-> db
                (assoc :templates templates))}))

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

(rf/reg-event-fx
 :projects2/infer-step
 in-projects2
 (fn [{:keys [db]} [_ project]]
   (let [steps ["goal", "consumers", "providers", "coverage", "actions", "review"]
         first-invalid-step (first (filter #(not (s/valid? (keyword "planwise.model.project" (str % "-step")) project)) steps))
         selected-step (if (not-empty first-invalid-step) first-invalid-step "review")]
     {:navigate (routes/projects2-show-with-step {:id (:id project) :step selected-step})})))

(rf/reg-event-fx
 :projects2/navigate-to-step-project
 in-projects2
 (fn [{:keys [db]} [_ project-id step]]
   {:navigate (routes/projects2-show-with-step {:id project-id :step step})}))


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
                 (some-> list
                         (utils/update-by-id (:id current-project)
                                             #(-> %
                                                  (assoc :state (:state current-project))
                                                  (assoc :name (:name current-project))
                                                  (assoc :region-id (:region-id current-project))))))))))


(rf/reg-event-fx
 :projects2/project-not-found
 in-projects2
 (fn [_ _]
   {:navigate (routes/projects2)}))

(rf/reg-event-fx
 :projects2/get-project
 in-projects2
 (fn [{:keys [db]} [_ id]]
   {:dispatch [:scenarios/invalidate-scenarios]
    :api (assoc (api/get-project id)
                :on-success [:projects2/save-project-data]
                :on-failure [:projects2/project-not-found])}))

;; NOTE: the start-project only works for the current project since
;; upon callback we are updating the local db current-project
(rf/reg-event-fx
 :projects2/start-project
 in-projects2
 (fn [{:keys [db]} [_ id]]
   {:dispatch-n [[:scenarios/invalidate-scenarios]
                 [:providers-set/load-providers-set]]
    :api (assoc (api/start-project! id)
                :on-success [:projects2/save-project-data]
                :on-failure [:projects2/project-not-found])}))

;; NOTE: the reset-project only works for the current project since
;; upon callback we are updating the local db current-project
(rf/reg-event-fx
 :projects2/reset-project
 in-projects2
 (fn [{:keys [db]} [_ id]]
   {:api (assoc (api/reset-project! id)
                :on-success [:projects2/save-project-data]
                :on-failure [:projects2/project-not-found])}))

(rf/reg-event-fx
 :projects2/delete-project
 in-projects2
 (fn [{:keys [db]} [_ id]]
   {:api       (api/delete-project! id)
    :navigate  (routes/projects2)
    :dispatch [:providers-set/load-providers-set]
    :db   (-> db
              (assoc :current-project nil)
              (update :list #(seq (utils/remove-by-id % id))))}))

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
 (fn [{:keys [db]} [_ {:keys [config] :as project}]]
   (let [current-project (:current-project db)]
     (if (= (:id project) (:id current-project))
        ;; keep current values of current-project except the once that could be updated from server
        ;; to prevent replacing data that have not been saved in server yet
       (let [update-config-if-necessary (if (= (:coverage-algorithm current-project)
                                               (:coverage-algorithm project))
                                          (:config current-project)
                                          config)
             updated-project (-> current-project
                                 (assoc :providers (:providers project))
                                 (assoc :coverage-algorithm (:coverage-algorithm project))
                                 (assoc :config update-config-if-necessary))]
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

;;------------------------------------------------------------------------------
;; Filtering by tags

(rf/reg-event-fx
 :projects2/save-tag
 in-projects2
 (fn [{:keys [db]} [_ tag]]
   (let [path [:current-project :config :providers :tags]
         n (count (get-in db path))]
     {:db       (update-in db path (comp vec conj) tag)
      :dispatch [:projects2/persist-current-project]})))

(rf/reg-event-fx
 :projects2/delete-tag
 in-projects2
 (fn [{:keys [db]} [_ index]]
   (let [path  [:current-project :config :providers :tags]
         tags* (utils/remove-by-index (get-in db path) index)]
     {:db       (assoc-in db path tags*)
      :dispatch [:projects2/persist-current-project]})))

;;------------------------------------------------------------------------------
;; Tab actions

(rf/reg-event-fx
 :projects2/project-scenarios
 in-projects2
 (fn [{:keys [db]} _]
   {:navigate (routes/projects2-scenarios {:id (get-in db [:current-project :id])})}))

(rf/reg-event-fx
 :projects2/project-settings
 in-projects2
 (fn [{:keys [db]} _]
   {:navigate (routes/projects2-settings {:id (get-in db [:current-project :id])})}))

;;------------------------------------------------------------------------------
;; Create actions

(rf/reg-event-fx
 :projects2/create-action
 in-projects2
 (fn [{:keys [db]} [_ action]]
   (let [path [:current-project :config :actions action]
         idx (count (get-in db path))]
     {:db         (update-in db path (comp vec conj) {:id (str (name action) "-" idx)})
      :dispatch   [:projects2/persist-current-project]})))

(rf/reg-event-fx
 :projects2/delete-action
 in-projects2
 (fn [{:keys [db]} [_ action index]]
   (let [path  [:current-project :config :actions action]
         actions* (utils/remove-by-index (get-in db path) index)]
     {:db       (assoc-in db path actions*)
      :dispatch [:projects2/persist-current-project]})))
