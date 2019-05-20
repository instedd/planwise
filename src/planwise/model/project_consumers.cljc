(ns planwise.model.project-consumers
  (:require [clojure.spec.alpha :as s]
            [clojure.string :refer [blank?]]))

(s/def ::target number?)
(s/def ::unit-name (s/and string? (comp not blank?)))
(s/def ::demographics (s/keys :req-un [::unit-name ::target]))
(s/def ::source-set-id number?)

(s/def ::config (s/keys :req-un [::demographics]))
(s/def ::validation (s/keys :req-un [::source-set-id ::config]))
