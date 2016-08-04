(ns planwise.client.components.progress-bar)

;; Progress bar

(defn progress-bar
  ([value]
   (let [percent (* 100 (min 1 (max 0 value)))]
     [:div.progress-bar
      [:div.progress-filled {:style {:width (str percent "%")}}]]))
  ([numerator denominator]
   (let [quotient (if (zero? denominator) 0 (/ numerator denominator))]
     (progress-bar quotient))))
