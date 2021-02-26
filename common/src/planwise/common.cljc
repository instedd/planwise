(ns planwise.common)

(defn is-budget
  [analysis-type]
  (= analysis-type "budget"))

(def is-action (complement is-budget))

(def currency-symbol "$")
