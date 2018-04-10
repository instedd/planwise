(ns planwise.client.scenarios.db)

(def initial-db
  {:view-state :current-scenario
   :rename-dialog            nil
   :current-scenario         nil})

(def initial-rename-dialog
  {:value ""})

(defn show-dialog?
  [state]
  (#{:rename-dialog} state))