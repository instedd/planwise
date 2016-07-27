(ns planwise.client.handlers
  (:require [planwise.client.db :as db]
            [planwise.client.playground.handlers :as playground]
            [planwise.client.projects.handlers :as projects]
            [planwise.client.datasets.handlers]
            [planwise.client.regions.handlers :as regions]
            [re-frame.utils :as c]
            [re-frame.core :refer [dispatch register-handler]]))

;; Event handlers
;; -----------------------------------------------------------------------

(register-handler
 :initialise-db
 (fn [_ _]
   (dispatch [:projects/fetch-facility-types])
   (dispatch [:regions/load-regions])
   db/initial-db))

(defmulti on-navigate (fn [db page params] page))

(defmethod on-navigate :projects [db page {id :id, section :section, :as page-params}]
  (let [id (js/parseInt id)]
    (when (not= id (get-in db [:projects :current :project-data :id]))
      (dispatch [:projects/load-project id]))
    (if (= :transport section)
      (dispatch [:projects/load-isochrones]))
    db))

(defmethod on-navigate :home [db _ _]
  (dispatch [:projects/load-projects])
  db)

(defmethod on-navigate :datasets [db _ _]
  (dispatch [:datasets/initialise!])
  db)

(defmethod on-navigate :playground [db _ _]
  (playground/fetch-facilities-with-isochrones :immediate (:playground db))
  db)

(defmethod on-navigate :default [db _ _]
  db)

(register-handler
 :navigate
 (fn [db [_ {page :page, :as params}]]
   (let [new-db (assoc db
                 :current-page page
                 :page-params params)]
     (on-navigate new-db page params))))

(register-handler
 :message-posted
 (fn [db [_ message]]
   (case message
     "authenticated" (dispatch [:datasets/reload-info])
     (c/warn "Invalid message received " message))
   db))

(register-handler
 :tick
 (fn [db [_ time]]
   (when (= 0 (mod time 3000))
     (dispatch [:datasets/update-import-status]))
   db))
