(ns planwise.client.scenarios.db
  (:require [planwise.client.asdf :as asdf]))

(def initial-db
  {:view-state :current-scenario
   :rename-dialog            nil
   :current-scenario         nil
   :changeset-dialog         nil
   :list-scope               nil
   :list                     (asdf/new nil)})


(defmulti initial-provider :action-name)

(defmethod initial-provider :create
  [props]
  (merge {:action     "create-provider"
          :investment 0
          :capacity   0
          :id         (str (random-uuid))}
         props))

(defmethod initial-provider :upgrade
  [props]
  {:action     "upgrade-provider"
   :investment 0
   :capacity   0
   :id         (:id props)})

(defmethod initial-provider :increase
  [props]
  {:action     "increase-provider"
   :investment 0
   :capacity   0
   :id         (:id props)})
