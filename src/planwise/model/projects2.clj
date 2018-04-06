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
      (default [:coverage :filter-options] {})))

(s/def ::target (s/nilable number?))
(s/def ::budget (s/nilable number?))
(s/def ::filter-options map?)

(s/def ::demographics (s/keys :req-un [::target]))
(s/def ::actions (s/keys :req-un [::budget]))
(s/def ::coverage (s/keys :req-un [::filter-options]))

(s/def ::config (s/nilable (s/keys :req-un [::demographics ::actions ::coverage])))
(s/def ::id number?)
(s/def ::name string?)
(s/def ::dataset-id (s/nilable number?))
(s/def ::region-id (s/nilable number?))
(s/def ::population-source-id (s/nilable number?))

(s/def ::project (s/keys :req-un [::id ::owner-id ::name ::config ::dataset-id ::population-source-id ::region-id]))
