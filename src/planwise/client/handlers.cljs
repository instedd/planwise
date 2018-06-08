(ns planwise.client.handlers
  (:require [planwise.client.db :as db]
            [planwise.client.api :as api]
            [planwise.client.routes :as routes]
            [planwise.client.projects.handlers :as projects]
            [planwise.client.current-project.handlers :as current-project]
            [planwise.client.datasets.handlers]
            [planwise.client.projects2.handlers]
            [planwise.client.providers-set.handlers]
            [planwise.client.sources.handlers]
            [planwise.client.scenarios.handlers]
            [planwise.client.coverage]
            [planwise.client.sources]
            [planwise.client.regions.handlers :as regions]
            [re-frame.core :as rf]))

;; Event handlers
;; -----------------------------------------------------------------------

(rf/reg-event-fx
 :initialise-db
 (fn [_ _]
   {:dispatch-n [[:regions/load-regions]
                 [:coverage/load-algorithms]]
    :db db/initial-db}))

(defmulti on-navigate (fn [page params] page))

(defmethod on-navigate :projects [page {id :id, section :section, token :token, :as page-params}]
  (let [id (js/parseInt id)]
    (if (= :access (keyword section))
      {:dispatch [:current-project/access-project id token]}
      {:dispatch [:current-project/navigate-project id section]})))

(defmethod on-navigate :default [_ _]
  nil)

(rf/reg-event-fx
 :navigate
 (fn [{:keys [db]} [_ {page :page, :as params}]]
   (let [new-db (assoc db
                       :current-page page
                       :page-params params)]
     (merge {:db new-db}
            (on-navigate page params)))))

(rf/reg-event-fx
 :signout
 (fn [_ [_]]
   {:api (assoc api/signout
                :on-success [:after-signout])}))

(rf/reg-event-fx
 :after-signout
 (fn [_ [_ data]]
   (let [url (or (:redirect-to data) (routes/home))]
     {:location url})))

(rf/reg-event-fx
 :message-posted
 (fn [_ [_ message]]
   (cond
     (= message "authenticated")
     {:dispatch [:datasets/load-resourcemap-info]}

     (#{"react-devtools-content-script"
        "react-devtools-bridge"
        "react-devtools-detector"}
      (aget message "source"))
     nil   ; ignore React dev tools messages

     true
     (rf/console :warn "Invalid message received " message))))

(rf/reg-event-fx
 :tick
 (fn [_ [_ time]]
   (when (= 0 (mod time 1000))
     {:dispatch [:datasets/refresh-datasets time]})))
