(ns planwise.model.scenarios
  (:require [clojure.spec.alpha :as s]))

(s/def ::investment int?)
(s/def ::capacity int?)
(s/def ::provider-id string?)
(s/def ::location map?)

(s/def :create-provider/action #{"create-provider"})

(s/def ::create-provider-change
  (s/keys :req-un [:create-provider/action ::investment ::capacity ::provider-id ::location]))

(s/def ::change-set
  (s/coll-of ::create-provider-change))
