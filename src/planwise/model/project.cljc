(ns planwise.model.project
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

;; Providers
(s/def ::provider-set-id number?)
(s/def ::capacity number?)
(s/def ::providers (s/keys :req-un [::capacity]))

;; Coverage
(s/def ::driving-time number?)
(s/def ::walking-time number?)
(s/def ::distance number?)

(s/def ::driving-options (s/keys :req-un [::driving-time]))
(s/def ::walking-options (s/keys :req-un [::walking-time]))
(s/def ::distance-options (s/keys :req-un [::distance]))
;; example:
;; (s/def ::public-transport-options (s/keys :req-un [::walking-time ::transport-type ::total-time]))
(s/def ::filter-options (s/or :driving-options ::driving-options
                              :walking-options ::walking-options
                              :distance-options ::distance-options))
(s/def ::coverage (s/keys :req-un [::filter-options]))

;; Actions
(s/def ::budget number?)
(s/def ::actions (s/keys :req-un [::budget]))

;; Config
(s/def ::config (s/keys :req-un [::demographics ::actions ::coverage ::providers]))

;; Project Starting
(s/def ::starting (s/keys :req-un [::id ::owner-id ::name ::config ::provider-set-id ::population-source-id ::region-id]))
