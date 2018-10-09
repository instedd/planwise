(ns planwise.model.scenarios
  (:require [clojure.spec.alpha :as s]))

(s/def ::investment int?)
(s/def ::capacity int?)
(s/def :planwise.scenarios.new-change/id string?)
(s/def :planwise.scenarios.change/id number?)
(s/def ::location map?)
(s/def ::name string?)

(s/def ::base-change
  (s/keys :req-un [::investment ::capacity]))

(s/def ::create-provider
  (s/keys :req-un [:planwise.scenarios.new-change/id ::location ::name]))

(s/def ::upgrade-provider
  (s/keys :req-un [:planwise.scenarios.change/id]))

(s/def ::increase-provider
  (s/keys :req-un [:planwise.scenarios.change/id]))


(defmulti change :action)
(defmethod change "create-provider" [_]
  (s/merge ::base-change ::create-provider))
(defmethod change "upgrade-provider" [_]
  (s/merge ::base-change ::upgrade-provider))
(defmethod change "increase-provider" [_]
  (s/merge ::base-change ::increase-provider))

(s/def ::change (s/multi-spec change :action))

(s/def ::change-set
  (s/coll-of ::change))