(ns planwise.model.coverage
  (:require [clojure.spec.alpha :as s]))

(s/def ::algorithm keyword?)
(s/def ::base-criteria (s/keys :req-un [::algorithm]))

(defmulti criteria-algo :algorithm)
(s/def ::coverage-criteria (s/multi-spec criteria-algo :algorithm))

(def buffer-distance-values
  (range 0 301 5))


;; Specs =====================================================================
;;

(s/def ::driving-time #{30 60 90 120})
(s/def ::driving-friction-criteria (s/keys :req-un [::driving-time]))

(s/def ::distance (set buffer-distance-values))
(s/def ::simple-buffer-criteria (s/keys :req-un [::distance]))

(s/def ::walking-time #{60 120 180})
(s/def ::walking-friction-criteria (s/keys :req-un [::walking-time]))

(s/def ::drive-walk-friction-criteria (s/keys :req-un [::driving-time ::walking-time]))

(defmethod criteria-algo :simple-buffer [_]
  (s/merge ::base-criteria ::simple-buffer-criteria))
(defmethod criteria-algo :walking-friction [_]
  (s/merge ::base-criteria ::walking-friction-criteria))
(defmethod criteria-algo :driving-friction [_]
  (s/merge ::base-criteria ::driving-friction-criteria))
(defmethod criteria-algo :drive-walk-friction [_]
  (s/merge ::base-criteria ::drive-walk-friction-criteria))


(defn valid-coverage-criteria?
  [algorithm filter-options]
  (s/valid? ::coverage-criteria
            (assoc filter-options :algorithm (keyword algorithm))))
