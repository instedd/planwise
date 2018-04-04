(ns planwise.client.population
  (:require [re-frame.core :as rf]
            [re-frame.core :refer [dispatch subscribe]]
            [planwise.client.ui.rmwc :as m]))
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
 (fn [{:keys [db]} [_]]
   {:api (assoc load-population-sources
                :on-success [:population/sources-loaded])}))

(rf/reg-event-db
 :population/sources-loaded
 (fn [db [_ population-sources]]
   (assoc-in db [:population :list] population-sources)))

;; ----------------------------------------------------------------------------
;; Subs

(rf/reg-sub
 :population/dropdown-options
 (fn [db _]
   (let [list (get-in db [:population :list])]
     (mapv (fn [source] (let [{:keys [id name]} source] {:value id :label name})) list))))

;; ----------------------------------------------------------------------------
;; Views

(defn population-dropdown-component
  [{:keys [label value on-change]}]
  (let [list (subscribe [:population/dropdown-options])]
    (dispatch [:population/load-population-sources])
    (fn []
      [m/Select {:label (if (empty? @list) "No population layer available." label)
                 :disabled (empty? @list)
                 :value (str value)
                 :options @list
                 :onChange #(on-change (js/parseInt (-> % .-target .-value)))}])))
