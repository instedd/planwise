(ns planwise.model.project-actions
  (:require [clojure.spec.alpha :as s]
            [clojure.string :refer [blank?]]))

(s/def ::budget number?)
(s/def ::actions (s/keys :req-un [::budget]))
(s/def ::config (s/keys :req-un [::actions]))

(s/def ::validation (s/keys :req-un [::config]))
