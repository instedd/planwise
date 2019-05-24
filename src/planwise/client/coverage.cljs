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
             {:value key
              :label (:label algo-blurb)})
           algorithms))))

(rf/reg-sub
 :coverage/supported-algorithms
 (fn [db _]
   (get-in db [:coverage :algorithms])))

;; ----------------------------------------------------------------------------
;; Views

(defn- disabled-input-component
  [{:keys [label value options]}]
  [common2/text-field {:type     "text"
                       :label    label
                       :value    (utils/label-from-options options (str value) "")
                       :disabled true}])

(defn- criteria-option-select-component
  [{:keys [label value options on-change]}]
  (let [sorted-options  (sort-by :value options)
        step            (- (:value (second sorted-options)) (:value (first sorted-options)))]
    [m/Slider {:value    value
               :onInput  #(on-change (-> % .-detail .-value))
               :onChange #(on-change (-> % .-detail .-value))
               :min      (:value (first sorted-options))
               :max      (:value (last sorted-options))
               :discrete true
               :step     step}]))

(defn- criteria-option
  [{:keys [config value on-change disabled?]}]
  (let [options   (:options config)
        label     (:label config)
        component (if disabled?
                    disabled-input-component
                    criteria-option-select-component)]
    [component {:label     label
                :value     value
                :options   options
                :on-change on-change}]))

(defn coverage-algorithm-filter-options
  [{:keys [coverage-algorithm value on-change empty disabled?]}]
  (let [list      (rf/subscribe [:coverage/supported-algorithms])
        criteria  (get-in @list [(keyword coverage-algorithm) :criteria])]
    (cond
      (nil? criteria) empty
      :else [:div {:class-name "fields-vertical"}
             (map (fn [[key config]]
                    [criteria-option {:key key
                                      :config config
                                      :value (get value key)
                                      :on-change #(on-change (assoc value key %))
                                      :disabled? disabled?}])
                  criteria)])))
