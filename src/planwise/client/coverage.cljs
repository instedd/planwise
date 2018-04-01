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
; (defn filter-options-slider
;     [:div
;       [:h3 label]
;       [:h5 description]
;       [m/Slider {:discrete true
;                  :displayMarkers true
;                  :value (js/parseInt (-> % .-target .-value))
;                  :on-change #(dispatch [:projects2/save-key [:config :coverage :target] ])
;                  :step 30
;                  :max 120 }]])


(defn coverage-filter-dropdown-component
  [{:keys [name value on-change]}]
  (let [list (rf/subscribe [:coverage/supported-algorithms])
        current-project (rf/subscribe [:projects2/current-project])]
    (cond
      (nil? (:dataset-id @current-project)) [:div "First choose dataset."]
      (nil? name) (rf/dispatch [:projects2/get-project-data])
      :else
      (fn []
         (let [{:keys[label criteria]} ((keyword name) @list)
                first-key   (first (keys criteria))
                options     (get-in criteria [first-key :options])]
          [m/Select {:label label
                      :disabled (empty? options)
                      :value value
                      :options options
                      :onChange #(on-change (js/parseInt (-> % .-target .-value)))}])))))

