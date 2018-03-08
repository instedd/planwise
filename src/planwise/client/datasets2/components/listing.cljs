(ns planwise.client.datasets2.components.listing
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [re-com.popover :as popover]
            [re-com.core :refer-macros [handler-fn]]
            [clojure.string :as string]
            [planwise.client.utils :as utils]
            [planwise.client.components.common :as common]
            [planwise.client.datasets2.db :as db]
            [planwise.client.utils :refer [format-percentage]]
            [re-frame.utils :as c]))

(defn new-dataset2-button
  []
  [:button.primary
   {:on-click
    #(dispatch [:datasets2/create-dataset2!])}
   "New Dataset"])

(defn no-datasets2-view []
  [:div.empty-list
   [common/icon :box]
   [:p "You have no datasets yet"]
   [:div
    [new-dataset2-button]]])

(defn dataset2-card
  [dataset]
  (let [name (:name dataset)]
    [:div.dataset-card
     [:h1 name]]))

(defn datasets2-list
  [datasets]
  [:div
   [:ul.dataset-list
    (for [dataset datasets]
      [:li {:key (:id dataset)}
       [dataset2-card dataset]])]])
