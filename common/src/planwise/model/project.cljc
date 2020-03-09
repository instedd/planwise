(ns planwise.model.project
  (:require [planwise.model.coverage :as coverage]
            [clojure.spec.alpha :as s]
            [clojure.string :refer [blank?]]))

;; ----------------------------------------------------------------------
;; Project starting validation

;; Goal
(s/def ::id number?)
(s/def ::name (s/and string? (comp not blank?)))
(s/def ::region-id number?)

;; Demographics
(s/def ::source-set-id number?)
(s/def ::target number?)
(s/def ::unit-name string?)
(s/def ::demographics (s/keys :req-un [::unit-name ::target]))

;; Providers
(s/def ::provider-set-id (s/nilable number?))
(s/def ::capacity number?)
(s/def ::providers (s/keys :req-un [::capacity]))

;; Coverage
(s/def ::driving-time number?)
(s/def ::walking-time number?)
(s/def ::distance number?)
(s/def ::coverage-algorithm string?)

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
(s/def :planwise.model.project-consumers/config (s/keys :req-un [:planwise.model.project/demographics]))
(s/def :planwise.model.project-providers/config (s/keys :req-un [:planwise.model.project/providers]))
(s/def :planwise.model.project-coverage/config (s/keys :req-un [:planwise.model.project/coverage]))
(s/def :planwise.model.project-actions/config (s/keys :req-un [:planwise.model.project/actions]))

(defn valid-project-coverage?
  [{:keys [coverage-algorithm config]}]
  (let [filter-options (get-in config [:coverage :filter-options])]
    (coverage/valid-coverage-criteria? coverage-algorithm filter-options)))

(defn ensure-valid-coverage
  [{:keys [config] :as project}]
  (let [updated-config (if (valid-project-coverage? project)
                         config
                         (assoc-in config [:coverage :filter-options] {}))]
    (assoc project :config updated-config)))


(s/def ::starting (s/keys :req-un [::id ::owner-id ::name ::config ::coverage-algorithm ::source-set-id ::region-id]))

(defn valid-starting-project?
  [project]
  (and (valid-project-coverage? project)
       (s/valid? ::starting project)))


;; Project Starting

(s/def ::goal-step (s/keys :req-un [:planwise.model.project/name :planwise.model.project/region-id]))
(s/def ::consumers-step (s/keys :req-un [:planwise.model.project/source-set-id :planwise.model.project-consumers/config]))
(s/def ::providers-step (s/keys :req-un [::provider-set-id :planwise.model.project-providers/config]))
(s/def ::coverage-step (s/and
                        #(valid-project-coverage? %)
                        (s/keys :req-un [::coverage-algorithm :planwise.model.project-coverage/config])))
(s/def ::actions-step (s/keys :req-un [:planwise.model.project-actions/config]))
(s/def ::review-step #(valid-starting-project? %))
