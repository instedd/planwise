(ns planwise.client.slider
  (:require [reagent.core :as reagent]
            [goog.string :as gstring]
            [goog.string.format]))

(defn format-seconds [secs]
  (let [minutes (.trunc js/Math (/ secs 60))
        hours (.trunc js/Math (/ secs 3600))
        mins (- minutes (* 60 hours))]
    (gstring/format "%d:%02d" hours mins)))

(defn threshold-slider [{:keys [value on-change]}]
  (let []
    [:div.threshold-control
     [:input {:type "range"
              :min 300 :max 28800 :step 300
              :value value
              :on-change (fn [e]
                           (let [new-value (js/parseInt (-> e .-target .-value))]
                             (when on-change
                               (on-change new-value))))}
      [:span.small (format-seconds value)]]]))

(defn decimal-slider [{:keys [value on-change]}]
  (let []
    [:div.threshold-control
     [:input {:type "range"
              :min 0 :max 100
              :value (* value 100)
              :on-change (fn [e]
                           (let [new-value (/ (js/parseInt (-> e .-target .-value)) 100.0)]
                             (when on-change
                               (on-change new-value))))}
      [:span.small (gstring/format "%.2f" value)]]]))
