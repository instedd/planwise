(ns planwise.model.project-review
  (:require [clojure.spec.alpha :as s]
            [clojure.string :refer [blank?]]))


(s/def ::id number?)
(s/def ::name (s/and string? (comp not blank?)))
(s/def ::region-id number?)

;; Demographics
(s/def ::source-set-id number?)
(s/def ::target number?)
(s/def ::unit-name string?)
(s/def ::demographics (s/keys :req-un [::unit-name ::target]))

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


(s/def ::filter-options (s/or :driving-options ::driving-options
                              :walking-options ::walking-options
                              :distance-options ::distance-options))
(s/def ::coverage (s/keys :req-un [::filter-options]))

;; Actions
(s/def ::budget number?)
(s/def ::actions (s/keys :req-un [::budget]))

;; Config
(s/def ::config (s/keys :req-un [::demographics ::actions ::coverage ::providers]))

(s/def ::validation (s/keys :req-un [::id ::owner-id ::name ::config ::provider-set-id ::source-set-id ::region-id]))
