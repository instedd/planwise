(ns planwise.client.sources.components.dropdown
  (:require [re-frame.core :as rf]
            [re-frame.core :refer [dispatch subscribe]]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common2 :as common2]
            [planwise.client.utils :as utils]
            [planwise.client.ui.filter-select :as filter-select]
            [planwise.client.ui.rmwc :as m]))

(def in-sources (rf/path [:sources]))

(rf/reg-sub
 :sources/dropdown-options
 (fn [_]
   [(subscribe [:sources/list])
    (subscribe [:projects2/source-types])])
 (fn [[list types] _]
   (filter (fn [{:keys [type]}] (types type)) list)))

;; ----------------------------------------------------------------------------
;; Views

(defn sources-dropdown-component
  [attrs]
  (let [props (merge {:choices   @(rf/subscribe [:sources/dropdown-options])
                      :label-fn  :name
                      :render-fn (fn [source] [:div.option-row
                                               [:span (:name source)]
                                               [:span.option-context (:value source)]])}
                     attrs)]
    (into [filter-select/single-dropdown] (mapcat identity props))))
