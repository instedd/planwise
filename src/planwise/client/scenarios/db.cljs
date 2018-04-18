(ns planwise.client.scenarios.db
  (:require [planwise.client.asdf :as asdf]))

(def initial-db
  {:view-state :current-scenario
   :rename-dialog            nil
   :current-scenario         nil
   :changeset-dialog         nil
   :list-scope               nil
   :list                     (asdf/new nil)})

(defn initial-site
  [props]
  (merge {:action "create-site"
          :investment 0
          :capacity 0
          :site-id (str (random-uuid))}
         props))
