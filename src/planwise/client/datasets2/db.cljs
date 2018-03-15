(ns planwise.client.datasets2.db
  (:require [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by-id]]))

(def initial-db
  {:view-state   :list
   :list         (asdf/new nil)})

(defn show-dialog?
  [state]
  (case state
    (:create-dialog :creating) true
    false))
