(ns planwise.client.datasets.components.listing
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.utils :as utils]
            [planwise.client.components.common :as common]
            [planwise.client.datasets.db :as db]))


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
  [{:keys [id name description facility-count server-status] :as dataset}]
  [:div.dataset-card
   [:h1 name]
   [:h2 description]
   [:p
    [common/icon :location "icon-small"]
    (utils/pluralize facility-count "facility" "facilities")
    (str " (" (db/server-status->string server-status) ")")]])

(defn datasets-list
  [datasets]
  [:div
   [search-box (count datasets)]
   [:ul.dataset-list
    (for [dataset datasets]
      [:li {:key (:id dataset)}
       [dataset-card dataset]])]])
