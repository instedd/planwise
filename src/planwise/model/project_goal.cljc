(ns planwise.model.project-goal
  (:require [clojure.spec.alpha :as s]
            [clojure.string :refer [blank?]]))

(s/def ::name (s/and string? (comp not blank?)))
(s/def ::region-id number?)
(s/def ::validation (s/keys :req-un [::name ::region-id]))
