(ns planwise.client.providers-set.components.dropdown
  (:require [re-frame.core :as rf]
            [planwise.client.ui.filter-select :as filter-select]))

(defn providers-set-dropdown-component
  [attrs]
  (let [props (merge {:choices   @(rf/subscribe [:providers-set/dropdown-options])
                      :label-fn  :label
                      :render-fn (fn [provider-set] [:div.option-row
                                               [:span (:label provider-set)]])}
                     attrs)]
    (into [filter-select/single-dropdown] (mapcat identity props))))
