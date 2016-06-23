(ns planwise.client.handlers
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [planwise.client.db :as db]
            [planwise.client.projects.handlers]
            [planwise.client.playground.handlers :as playground]
            [re-frame.core :refer [dispatch register-handler]]))


;; Event handlers
;; -----------------------------------------------------------------------

(register-handler
 :initialise-db
 (fn [_ _]
   (playground/fetch-facilities-with-isochrones)
   db/initial-db))

(register-handler
 :navigate
 (fn [db [_ page]]
   (assoc db :current-page page)))
