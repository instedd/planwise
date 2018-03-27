(ns planwise.client.population
  (:require [re-frame.core :as rf]
            [planwise.client.ui.rmwc :as m]))

(def load-population-sources
  {:method    :get
   :section   :show
   :uri       "/api/population"})

(rf/reg-event-fx
 :population/load-population-sources
 (fn [{:keys [db]} [_]]
    {:api (assoc load-population-sources
              :on-success [:population/sources-loaded])}))

(rf/reg-event-db
 :population/sources-loaded
 (fn [db [_ population-sources]]
   (assoc-in db [:population :list] population-sources)))

(rf/reg-sub
 :population/list
 (fn [db _]
   (let [list (get-in db [:population :list])]
     (mapv (fn [source] (let [{:keys [id name]} source]{:value id :label name})) list))))

(defn population-dropdown-component
  [{:keys [label value on-change]}]
    (let [list (subscribe [:population/list])]
    (fn []
      (do
        (dispatch [:population/load-population-sources]))
        [m/Select {:label (if (empty? @list) "No sources available" label)
                   :disabled (empty? @list)
                   :value (str value)
                   :options @list
                   :onChange #(on-change (js/parseInt (-> % .-target .-value)))}])))
