(ns planwise.common
  (:require [clojure.string :refer [lower-case]]))

(defn is-budget
  [analysis-type]
  (= analysis-type "budget"))

(def is-action (complement is-budget))

(def currency-symbol "$")

(defn pluralize
  ([count singular]
   (pluralize count singular (str singular "s")))
  ([count singular plural]
   (let [noun (if (= 1 count) singular plural)]
     (str count " " noun))))

(defn- get-project-unit
  [project path value lowercase?]
  ((if lowercase? lower-case identity) (or (not-empty (get-in project path)) value)))

(defn get-consumer-unit
  ([project]
   (get-consumer-unit project true))
  ([project lowercase?]
   (get-project-unit project [:config :demographics :unit-name] "total population" lowercase?)))

(defn get-demand-unit
  ([project]
   (get-demand-unit project true))
  ([project lowercase?]
   (get-project-unit project [:config :demographics :demand-unit] "targets" lowercase?)))

(defn get-provider-unit
  ([project]
   (get-provider-unit project true))
  ([project lowercase?]
   (get-project-unit project [:config :providers :provider-unit] "providers" lowercase?)))

(defn get-capacity-unit
  ([project]
   (get-capacity-unit project true))
  ([project lowercase?]
   (get-project-unit project [:config :providers :capacity-unit] "units" lowercase?)))
