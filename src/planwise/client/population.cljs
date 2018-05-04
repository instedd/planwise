(ns planwise.client.population
  (:require [re-frame.core :as rf]
            [re-frame.core :refer [dispatch subscribe]]
            [planwise.client.asdf :as asdf]
            [planwise.client.ui.rmwc :as m]))

(def in-population (rf/path [:population]))

(def initial-db
  {:list (asdf/new nil)})

;; ----------------------------------------------------------------------------
;; API methods

(def load-population-sources
  {:method    :get
   :section   :show
   :uri       "/api/population"})

;; ----------------------------------------------------------------------------
;; Listing population sources

(rf/reg-event-fx
 :population/load-population-sources
 in-population
 (fn [{:keys [db]} [_]]
   {:api (assoc load-population-sources
                :on-success [:population/sources-loaded])
    :db  (update db :list asdf/reload!)}))

(rf/reg-event-db
 :population/sources-loaded
 in-population
 (fn [db [_ population-sources]]
   (update db :list asdf/reset! population-sources)))

;; ----------------------------------------------------------------------------
;; Subs

(rf/reg-sub
 :population/list
 (fn [db _]
   (get-in db [:population :list])))

(rf/reg-sub
 :population/dropdown-options
 (fn [db _]
   (let [list (asdf/value (get-in db [:population :list]))]
     (mapv (fn [source] (let [{:keys [id name]} source] {:value id :label name})) list))))

;; ----------------------------------------------------------------------------
;; Views

(defn population-dropdown-component
  [{:keys [label value on-change]}]
  (let [list    (subscribe [:population/list])
        options (subscribe [:population/dropdown-options])]
    (when (asdf/should-reload? @list)
      (dispatch [:population/load-population-sources]))
    [m/Select {:label (if (empty? @options) "No population layer available." label)
               :disabled (empty? @options)
               :value (str value)
               :options @options
               :onChange #(on-change (js/parseInt (-> % .-target .-value)))}]))

(defn population-disabled-input-component
  [{:keys [label value]}]
  (let [list        (subscribe [:population/list])
        options     (subscribe [:population/dropdown-options])
        filtered    (filter (fn[el](= (:value el) value)) @options)]
    (when (asdf/should-reload? @list)
      (dispatch [:population/load-population-sources]))
    [m/TextField {:type     "text"
                  :label    label
                  :value    (if (empty? filtered) "" (:label (first filtered)))
                  :disabled true}]))
