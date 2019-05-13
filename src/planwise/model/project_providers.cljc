(ns planwise.model.project-providers
  (:require [clojure.spec.alpha :as s]
            [clojure.string :refer [blank?]]))


(s/def ::provider-set-id number?)
(s/def ::capacity number?)
(s/def ::providers (s/keys :req-un [::capacity]))
(s/def ::config (s/keys :req-un [::providers]))
(s/def ::validation (s/keys :req-un [::provider-set-id]))
