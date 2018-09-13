(ns planwise.client.scenarios.db
  (:require [planwise.client.asdf :as asdf]))

(def initial-db
  {:view-state :current-scenario
   :rename-dialog            nil
   :current-scenario         nil
   :changeset-dialog         nil
   :list-scope               nil
   :list                     (asdf/new nil)})


(defmulti new-action :action-name)

(defmethod new-action :create
  [props]
  {:action     "create-provider"
   :investment 0
   :capacity   0
   :location   props
   :id         (str (random-uuid))})

(defmethod new-action :upgrade
  [props]
  {:action     "upgrade-provider"
   :investment 0
   :capacity   0
   :id         (:id props)})

(defmethod new-action :increase
  [props]
  {:action     "increase-provider"
   :investment 0
   :capacity   0
   :id         (:id props)})

(defn new-provider-from-change
  [change index]
  {:id             (:id change)
   :name           (str "New provider " index)
   :location       (:location change)
   :matches-filter true
   :change         change})
