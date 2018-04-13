(ns planwise.model.scenarios
  (:require [clojure.spec.alpha :as s]))

(s/def ::investment int?)
(s/def ::capacity int?)
(s/def ::site-id string?)
(s/def ::location map?)

(s/def :create-site/action #{"create-site"})

(s/def ::create-site-change
  (s/keys :req-un [:create-site/action ::investment ::capacity ::site-id ::location]))

(s/def ::change-set
  (s/coll-of ::create-site-change))
