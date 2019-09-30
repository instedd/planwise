(ns planwise.client.sources.db
  (:require [planwise.client.asdf :as asdf]))

(def initial-db
  {:list (asdf/new nil)})
