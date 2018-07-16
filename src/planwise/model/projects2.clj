(ns planwise.model.projects2
  (:require [clojure.spec.alpha :as s]))

(defn- default
  [map path value-default]
  (let [value (get-in map path)]
    (if (nil? value)
      (assoc-in map path value-default)
      map)))

(defn apply-default
  [config]
  (-> config
      (default [:demographics :target] nil)
      (default [:actions :budget] nil)
      (default [:coverage :filter-options] {})
      (default [:providers :capacity] 1)))

(s/def ::target (s/nilable number?))
(s/def ::budget (s/nilable number?))
(s/def ::upgrade-budget (s/nilable number?))
(s/def ::build (s/nilable (s/coll-of map? :kind vector? :distinct true)))
(s/def ::upgrade (s/nilable (s/coll-of map? :kind vector? :distinct true)))
(s/def ::filter-options map?)

(s/def ::demographics (s/keys :req-un [::target]))
(s/def ::actions (s/keys :req-un [::budget]
                         :opt-un [::build ::upgrade ::upgrade-budget]))
(s/def ::coverage (s/keys :req-un [::filter-options]))
(s/def ::providers (s/keys :req-un [::capacity]))

(s/def ::config (s/nilable (s/keys :req-un [::demographics ::actions ::coverage ::providers])))
(s/def ::id number?)
(s/def ::name string?)
(s/def ::provider-set-id (s/nilable number?))
(s/def ::region-id (s/nilable number?))
(s/def ::source-set-id (s/nilable number?))

(s/def ::project (s/keys :req-un [::id ::owner-id ::name ::config ::provider-set-id ::source-set-id ::region-id]))
