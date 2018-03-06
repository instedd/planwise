(ns planwise.model.datasets
  (:require [schema.core :as s]))

(def FieldTypeMapping s/Any)

(def Dataset
  {:id            s/Int
   :owner-id      s/Int
   :name          s/Str
   :description   s/Str
   :collection-id s/Int
   :mappings      {(s/optional-key :type) FieldTypeMapping}})

(defn owned-by?
  [dataset user-id]
  (= user-id (:owner-id dataset)))
