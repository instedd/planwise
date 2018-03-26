(ns planwise.client.population
  (:require [re-frame.core :as rf]))

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

