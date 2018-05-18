(ns planwise.client.datasets2.components.dropdown
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as string]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.components.common2 :as common2]
            [planwise.client.datasets2.db :as db]
            [planwise.client.ui.rmwc :as m]
            [re-frame.utils :as c]))

(defn- disabled-input-component
  [{:keys [label value options empty-label]}]
  [common2/text-field {:type     "text"
                       :label    label
                       :value    (utils/label-from-options options (str value) empty-label)
                       :disabled true}])

(defn- datasets-select-component
  [{:keys [label value options empty-label on-change]}]
  [m/Select {:label (if (empty? options) empty-label label)
             :disabled (empty? options)
             :value (str value)
             :options (sort-by :label options)
             :on-change #(on-change (js/parseInt (-> % .-target .-value)))}])

(defn datasets-dropdown-component
  [{:keys [label value on-change disabled?]}]
  (let [list      (subscribe [:datasets2/list])
        options   (subscribe [:datasets2/dropdown-options])
        component (if disabled?
                    disabled-input-component
                    datasets-select-component)]
    (when (asdf/should-reload? @list)
      (dispatch [:datasets2/load-datasets2]))
    [component {:label        label
                :value        value
                :options      @options
                :empty-label  "There are no datasets defined."
                :on-change    on-change}]))
