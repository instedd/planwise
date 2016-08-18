(ns planwise.model.datasets
  (:require [schema.core :as s]))

(def FieldTypeMapping s/Any)

(def Dataset
  {:id            s/Int
   :owner-id      s/Int
   :name          s/Str
   :collection-id s/Int
   :mappings      {(s/optional-key :type) FieldTypeMapping}})
