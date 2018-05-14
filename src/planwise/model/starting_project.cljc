(ns planwise.model.starting-project
  (:require [clojure.spec.alpha :as s]
            [clojure.string :refer [blank?]]))

;; ----------------------------------------------------------------------
;; Project starting validation

;; Goal
(s/def ::id number?)
(s/def ::name string?)
(s/def ::region-id number?)

;; Demographics
(s/def ::population-source-id number?)
(s/def ::target number?)
(s/def ::unit-name (comp not blank?))
(s/def ::demographics (s/keys :req-un [::unit-name ::target]))

;; Sites
(s/def ::dataset-id number?)
(s/def ::capacity number?)
(s/def ::sites (s/keys :req-un [::capacity]))

;; Coverage
(s/def ::filter-options map?)
(s/def ::coverage (s/keys :req-un [::filter-options]))

;; Actions
(s/def ::budget number?)
(s/def ::actions (s/keys :req-un [::budget]))

;; Config
(s/def ::config (s/keys :req-un [::demographics ::actions ::coverage ::sites]))

;; Project Starting
(s/def ::project-starting (s/keys :req-un [::id ::owner-id ::name ::config ::dataset-id ::population-source-id ::region-id]))
