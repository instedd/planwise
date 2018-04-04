(ns planwise.client.coverage
  (:require [re-frame.core :as rf]
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

(defn- criteria-option
  [{:keys [config value on-change]}]
  (let [options (map #(update % :value str) (:options config))]
    [m/Select {:label (:label config)
               :disabled (empty? options)
               :value (str value)
               :options options
               :onChange #(on-change (js/parseInt (-> % .-target .-value)))}]))

(defn coverage-algorithm-filter-options
  [{:keys [coverage-algorithm value on-change empty]}]
  (let [list (rf/subscribe [:coverage/supported-algorithms])
        criteria (get-in @list [(keyword coverage-algorithm) :criteria])]
    (cond
      (nil? criteria) empty
      :else
      [:div {:class-name "fields-vertical"}
       (map (fn [[key config]]
              [criteria-option {:key key
                                :config config
                                :value (get value key)
                                :on-change #(on-change (assoc value key %))}])
            criteria)])))
