(ns planwise.model.facilities
  (:require [schema.core :as s]))

(def GeoJSON s/Str)

(def FacilityType
  "A facility type"
  {:id   s/Int
   :name s/Str})

(def Facility
  "A health facility"
  {:id                         s/Int
   :name                       s/Str
   (s/optional-key :type)      s/Str
   (s/optional-key :type-id)   s/Int
   :lat                        s/Num
   :lon                        s/Num
   (s/optional-key :isochrone) GeoJSON})
