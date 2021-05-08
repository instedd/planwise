(ns planwise.client.sources.components.dropdown
  (:require [re-frame.core :as rf]
            [re-frame.core :refer [dispatch subscribe]]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common2 :as common2]
            [planwise.client.utils :as utils]
            [planwise.client.ui.rmwc :as m]))

(def in-sources (rf/path [:sources]))

(rf/reg-sub
 :sources/dropdown-options
 (fn [_]
   [(subscribe [:sources/list])
    (subscribe [:projects2/source-types])])
 (fn [[list types] _]
   (->> list
        (filter (fn [{:keys [type]}] (types type)))
        (mapv (fn [{:keys [id name type]}] {:value id :label name :type type})))))

;; ----------------------------------------------------------------------------
;; Views

(defn- sources-select-component
  [{:keys [label value options empty-label on-change]}]
  [m/Select {:label (if (empty? options) empty-label label)
             :disabled (empty? options)
             :value (str value)
             :options options
             :onChange #(on-change (js/parseInt (-> % .-target .-value)))}])

(defn sources-dropdown-component
  [{:keys [label value on-change disabled?]}]
  (let [options   (subscribe [:sources/dropdown-options])
        component (if (or disabled? (empty? @options))
                    common2/disabled-input-component
                    sources-select-component)]
    [component {:label        label
                :value        value
                :options      @options
                :empty-label  "No sources layer available."
                :on-change    on-change}]))