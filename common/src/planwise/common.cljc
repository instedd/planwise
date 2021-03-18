(ns planwise.common)

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
