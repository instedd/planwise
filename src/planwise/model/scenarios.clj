(ns planwise.model.scenarios
  (:require [schema.core :as s]))

(def CreateSiteChange
  {:action (s/enum "create-site")
   :investment s/Int
   :capacity s/Int
   :site-id s/Str})

(def ChangeSet
  [CreateSiteChange])
