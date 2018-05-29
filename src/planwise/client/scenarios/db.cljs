(ns planwise.client.scenarios.db
  (:require [planwise.client.asdf :as asdf]))

(def initial-db
  {:view-state :current-scenario
   :rename-dialog            nil
   :current-scenario         nil
   :changeset-dialog         nil
   :list-scope               nil
   :list                     (asdf/new nil)})

(defn initial-provider
  [props]
  (merge {:action "create-provider"
          :investment 0
          :capacity 0
          :provider-id (str (random-uuid))}
         props))
