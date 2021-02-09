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
      (default [:analysis-type] "budget")
      (default [:actions :budget] nil)
      (default [:coverage :filter-options] {})
      (default [:providers :capacity] 1)))

(s/def ::target (s/nilable number?))
(s/def ::analysis-type (s/nilable string?))
(s/def ::budget (s/nilable number?))
(s/def ::upgrade-budget (s/nilable number?))
(s/def ::build (s/nilable (s/coll-of map? :kind vector? :distinct true)))
(s/def ::upgrade (s/nilable (s/coll-of map? :kind vector? :distinct true)))
(s/def ::filter-options map?)

(s/def ::demographics (s/keys :req-un [::target]))
(s/def ::actions (s/keys :req-un [::budget]
                         :opt-un [::build ::upgrade ::upgrade-budget]))
(s/def ::coverage (s/keys :req-un [::filter-options]))
(s/def ::capacity number?)
(s/def ::providers (s/keys :req-un [::capacity]))

(defmulti attr-actions :analysis-type)
(defmethod attr-actions "budget" [_]
  (s/keys :req-un [::analysis-type ::actions]))
(defmethod attr-actions "action" [_]
  (s/keys :req-un [::analysis-type]))

(s/def ::config-base (s/keys :req-un [::demographics ::coverage ::providers]))
(s/def ::config (s/nilable (s/merge ::config-base (s/multi-spec attr-actions :analysis-type))))
(s/def ::id number?)
(s/def ::name string?)
(s/def ::provider-set-id (s/nilable number?))
(s/def ::region-id (s/nilable number?))
(s/def ::source-set-id (s/nilable number?))

(s/def ::project (s/keys :req-un [::id ::owner-id ::name ::config ::provider-set-id ::source-set-id ::region-id]))
