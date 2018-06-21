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
 (fn [db _]
   (let [list (asdf/value (get-in db [:sources :list]))]
     (mapv (fn [source] (let [{:keys [id name]} source] {:value id :label name})) list))))

;; ----------------------------------------------------------------------------
;; Views

(defn- disabled-input-component
  [{:keys [label value options empty-label]}]
  [common2/text-field {:type     "text"
                       :label    label
                       :value    (utils/label-from-options options value empty-label)
                       :disabled true}])

(defn- sources-select-component
  [{:keys [label value options empty-label on-change]}]
  [m/Select {:label (if (empty? options) empty-label label)
             :disabled (empty? options)
             :value (str value)
             :options options
             :onChange #(on-change (js/parseInt (-> % .-target .-value)))}])

(defn sources-dropdown-component
  [{:keys [label value on-change disabled?]}]
  (let [list      (subscribe [:sources/list])
        options   (subscribe [:sources/dropdown-options])
        component (if (or disabled? (empty? @options))
                    disabled-input-component
                    sources-select-component)]
    (when (asdf/should-reload? @list)
      (dispatch [:sources/load]))
    [component {:label        label
                :value        value
                :options      @options
                :empty-label  "No sources layer available."
                :on-change    on-change}]))