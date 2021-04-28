(ns planwise.client.providers-set.components.dropdown
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as string]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.components.common2 :as common2]
            [planwise.client.providers-set.db :as db]
            [planwise.client.ui.rmwc :as m]
            [re-frame.utils :as c]))

(defn- providers-set-select-component
  [{:keys [label value options empty-label on-change]}]
  [m/Select {:label (if (empty? options) empty-label label)
             :disabled (empty? options)
             :value (str value)
             :options options
             :on-change #(on-change (js/parseInt (-> % .-target .-value)))}])

(defn providers-set-dropdown-component
  [{:keys [label value on-change disabled?]}]
  (let [list      (subscribe [:providers-set/list])
        options   (subscribe [:providers-set/dropdown-options])
        component (if (or disabled? (empty? @options))
                    common2/disabled-input-component
                    providers-set-select-component)]
    (when (asdf/should-reload? @list)
      (dispatch [:providers-set/load-providers-set]))
    [component {:label        label
                :value        value
                :options      @options
                :empty-label  "There are no providers-set defined."
                :on-change    on-change}]))
