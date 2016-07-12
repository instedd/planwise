(ns planwise.client.handlers
  (:require [planwise.client.db :as db]
            [planwise.client.playground.handlers :as playground]
            [planwise.client.projects.handlers :as projects]
            [re-frame.core :refer [dispatch register-handler]]))

;; Event handlers
;; -----------------------------------------------------------------------

(register-handler
 :initialise-db
 (fn [_ _]
   (playground/fetch-facilities-with-isochrones :immediate (:playground db/initial-db))
   db/initial-db))

(defmulti navigate-handler (fn [db [_ page & _]] page))

(defmethod navigate-handler :projects [db [_ page & params]]
  (let [id (js/parseInt (first params))
        db (assoc db
             :current-page page
             :page-params params)]
    (if (not= id (get-in db [:projects :current :id]))
      (do
        (dispatch [:projects/load-project id])
        (assoc-in db [:projects :current] nil))
      db)))

(defmethod navigate-handler :default [db [_ page & params]]
  (assoc db
   :current-page page
   :page-params params))

(register-handler
 :navigate
 navigate-handler)
