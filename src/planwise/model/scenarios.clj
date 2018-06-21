(ns planwise.model.scenarios
  (:require [clojure.spec.alpha :as s]))

(s/def ::investment int?)
(s/def ::capacity int?)
(s/def ::provider-id string?)
(s/def ::location map?)

(s/def ::action #{"create-provider"})
(s/def ::initial boolean?)

(s/def ::create-provider
  (s/keys :req-un [::action ::investment ::capacity ::provider-id ::location]))

(s/def ::change-set
  (s/coll-of ::create-provider))
