(ns planwise.client.projects.db
  (:require [schema.core :as s]
            [planwise.client.asdf :as asdf]))

;; Default data structures

(s/defschema Project s/Any)

(s/defschema ProjectsViewModel
  {:view-state    (s/enum [:list :create-dialog :creating])
   :list          (s/maybe (asdf/Asdf [Project]))
   :search-string s/Str})

(def initial-db
  {:view-state    :list
   :list          (asdf/new nil)
   :search-string ""})

(defn show-dialog?
  [state]
  (case state
    (:create-dialog :creating) true
    false))
