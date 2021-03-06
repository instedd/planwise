(ns planwise.client.coverage
  (:require [re-frame.core :as rf]
            [planwise.client.components.common2 :as common2]
            [planwise.client.utils :as utils]
            [planwise.client.ui.rmwc :as m]))

;; ----------------------------------------------------------------------------
;; API methods

(def load-coverage-algorithms
  {:method :get
   :uri    "/api/coverage/algorithms"})

;; ----------------------------------------------------------------------------
;; Events

(rf/reg-event-fx
 :coverage/load-algorithms
 (fn [_ _]
   {:api (assoc load-coverage-algorithms
                :on-success [:coverage/algorithms-loaded])}))

(rf/reg-event-db
 :coverage/algorithms-loaded
 (fn [db [_ algorithms]]
   (assoc-in db [:coverage :algorithms] algorithms)))

;; ----------------------------------------------------------------------------
;; Subs
(rf/reg-sub
 :coverage/algorithms-list
 (fn [db _]
   (let [algorithms (get-in db [:coverage :algorithms])]
     (mapv (fn [[key algo-blurb]]
             {:value (name key)
              :label (:label algo-blurb)})
           algorithms))))

(rf/reg-sub
 :coverage/supported-algorithms
 (fn [db _]
   (get-in db [:coverage :algorithms])))

;; ----------------------------------------------------------------------------
;; Views

(defn- criteria-option-select-component
  [{:keys [label value options on-change]}]
  (let [sorted-options  (sort-by :value options)
        step            (- (:value (second sorted-options)) (:value (first sorted-options)))
        value-label     (:label (first (filter #(= value (:value %)) options)))]
    [:div.coverage-setting
     [:label label]
     [m/Slider {:value    value
                :onInput  #(on-change (-> % .-detail .-value))
                :onChange #(on-change (-> % .-detail .-value))
                :min      (:value (first sorted-options))
                :max      (:value (last sorted-options))
                :discrete true
                :step     step}]
     [:p value-label]]))

(defn- criteria-option
  [{:keys [config value on-change disabled?]}]
  (let [options   (:options config)
        label     (:label config)
        component (if disabled?
                    common2/disabled-input-component
                    criteria-option-select-component)]
    [component {:label     label
                :value     (or value 0)
                :options   options
                :on-change on-change}]))

(defn coverage-algorithm-filter-options
  [{:keys [coverage-algorithm value on-change empty disabled?]}]
  (let [list         (rf/subscribe [:coverage/supported-algorithms])
        criteria     (get-in @list [(keyword coverage-algorithm) :criteria])
        valid-keys   (keys criteria)
        update-value (fn [key change]
                       (select-keys (merge value {key change}) valid-keys))]
    (cond
      (nil? criteria) empty
      :else [:div {:class-name "fields-vertical"}
             (map (fn [[key config]]
                    [criteria-option {:key key
                                      :config config
                                      :value (get value key)
                                      :on-change #(on-change (update-value key %))
                                      :disabled? disabled?}])
                  criteria)])))
