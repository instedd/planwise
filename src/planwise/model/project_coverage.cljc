(ns planwise.model.project-coverage
  (:require [clojure.spec.alpha :as s]
            [clojure.string :refer [blank?]]))


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

(s/def ::config (s/keys :req-un [::coverage]))
(s/def ::validation (s/keys :req-un [::config]))
