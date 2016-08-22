(ns planwise.client.datasets.components.listing
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.utils :as utils]))


(defn new-dataset-button
  []
  [:button.primary
   {:on-click
    #(dispatch [:datasets/begin-new-dataset])}
   "New Dataset"])

(defn search-box
  [dataset-count]
  (let [search-string (subscribe [:datasets/search-string])]
    (fn [dataset-count]
      [:div.search-box
       [:div (utils/pluralize dataset-count "dataset")]
       [:input
        {:type "search"
         :placeholder "Search datasets..."
         :value @search-string
         :on-change #(dispatch [:datasets/search (-> % .-target .-value str)])}]
       [new-dataset-button]])))

(defn no-datasets-view []
  [:div.empty-list
   [:img {:src "/images/empty-datasets.png"}]
   [:p "You have no datasets yet"]
   [:div
    [new-dataset-button]]])

(defn dataset-card
  [{:keys [id name description facility-count] :as dataset}]
  [:div.dataset-card
   [:h1 name]
   [:h2 description]
   [:p (utils/pluralize facility-count "facility" "facilities")]])

(defn datasets-list
  [datasets]
  [:ul.dataset-list
   (for [dataset datasets]
     [:li {:key (:id dataset)}
      [dataset-card dataset]])])
