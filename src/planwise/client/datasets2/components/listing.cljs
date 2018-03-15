(ns planwise.client.datasets2.components.listing
  (:require [re-frame.core :as rf]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]))


(defn new-dataset-button []
  [:button.primary
   {:on-click
    #(rf/dispatch [:datasets2/begin-new-dataset])}
   "New Dataset"])

(defn no-datasets2-view []
  [:div
   [common/icon :box]
   [:p "You have no datasets yet"]
   [:div
    [new-dataset-button]]])

(defn dataset2-card
  [dataset]
  (let [name (:name dataset)]
    [:div.dataset-card
     [:h1 name]]))

(defn datasets2-list
  [datasets]
  [:div
   [:div.search-box
    [:div (utils/pluralize (count datasets) "dataset")]
    [new-dataset-button]]
   [:ul.dataset-list
    (for [dataset datasets]
      [:li {:key (:id dataset)}
       [dataset2-card dataset]])]])
