(ns planwise.client.handlers
  (:require [planwise.client.db :as db]
            [planwise.client.playground.handlers :as playground]
            [planwise.client.projects.handlers :as projects]
            [planwise.client.regions.handlers :as regions]
            [re-frame.core :refer [dispatch register-handler]]))

;; Event handlers
;; -----------------------------------------------------------------------

(register-handler
 :initialise-db
 (fn [_ _]
   (playground/fetch-facilities-with-isochrones :immediate (:playground db/initial-db))
   db/initial-db))

(defmulti on-navigate (fn [db page params] page))

(defmethod on-navigate :projects [db page {id :id}]
  (let [id (js/parseInt id)]
    (when (not= id (get-in db [:projects :current :id]))
      (dispatch [:projects/load-project id]))
    db))

(defmethod on-navigate :home [db _ _]
  (dispatch [:projects/load-projects])
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
