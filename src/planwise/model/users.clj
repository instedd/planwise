(ns planwise.model.users
  (:require [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def User
  "A system user"
  {:id         s/Int
   :email      s/Str
   :full-name  (s/maybe s/Str)
   :last-login (s/maybe DateTime)})
