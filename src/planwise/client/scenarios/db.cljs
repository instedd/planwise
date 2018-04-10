(ns planwise.client.scenarios.db)

(def initial-db
  {:view-state :current-scenario
   :rename-dialog            nil
   :current-scenario         nil})

(def initial-new-site
  {:create-site/action "create-site"
   :investment 0
   :capacity 0
   :site-id "a"})
