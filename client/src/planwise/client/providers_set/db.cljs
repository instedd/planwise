(ns planwise.client.providers-set.db
  (:require [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by-id]]
            [clojure.spec.alpha :as s]))

(s/def ::view-state #{:list :create-dialog :creating})
(s/def ::last-error (s/nilable string?))
(s/def ::name string?)
(s/def ::coverage keyword?)

(def initial-new-provider-set
  {:name     ""
   :js-file  nil
   :coverage nil})

(def initial-db
  {:view-state   :list
   :last-error   nil
   :list         (asdf/new nil)
   :new-provider-set  initial-new-provider-set})

(defn show-dialog?
  [state]
  (#{:create-dialog :creating} state))
