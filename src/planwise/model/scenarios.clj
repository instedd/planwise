(ns planwise.model.scenarios
  (:require [clojure.spec.alpha :as s]))

(s/def ::investment int?)
(s/def ::capacity int?)
(s/def ::provider-id string?)
(s/def ::location map?)

(s/def ::action #{"create-provider"})

(s/def ::create-provider
  (s/keys :req-un [::action ::investment ::capacity ::provider-id ::location]))

(s/def ::initial-provider
  (s/keys :req-un [::provider-id ::capacity ::location]))

(s/def ::change-set
  (s/coll-of (s/or ::create-provider ::initial-provider)))
