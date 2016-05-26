(ns viewer.slider
  (:require [reagent.core :as reagent]))

(defn threshold-slider [{:keys [value on-change]}]
  (let []
    [:div.threshold-control
     [:input {:type "range"
              :min 1 :max 300
              :value value
              :on-change (fn [e]
                           (let [new-value (js/parseInt (-> e .-target .-value))]
                             (when on-change
                               (on-change new-value))))}
      [:span.small (str value)]]]))
