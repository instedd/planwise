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

(register-handler
 :navigate
 (fn [db [_ page & params]]
   (assoc db
          :current-page page
          :page-params params)))
