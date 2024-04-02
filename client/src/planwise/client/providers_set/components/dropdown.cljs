(ns planwise.client.providers-set.components.dropdown
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.ui.filter-select :as filter-select]))

(defn providers-set-dropdown-component
  [attrs]
  (let [list  (rf/subscribe [:providers-set/list])
        props (merge {:choices   @(rf/subscribe [:providers-set/dropdown-options])
                      :label-fn  :label
                      :render-fn (fn [provider-set] [:div.option-row
                                               [:span (:label provider-set)]])}
                     attrs)]
    (when (asdf/should-reload? @list)
      (rf/dispatch [:providers-set/load-providers-set]))
    (into [filter-select/single-dropdown] (mapcat identity props))))
