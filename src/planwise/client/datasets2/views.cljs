(ns planwise.client.datasets2.views
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.datasets2.db :as db]
            [planwise.client.datasets2.components.listing :refer [no-datasets2-view datasets2-list]]
            [planwise.client.datasets2.components.new-dataset :refer [new-dataset-dialog]]
            [planwise.client.components.common :as common]))

;; ----------------------------------------------------------------------------
;; Datasets2 list

(defn- listing-updated-datasets
  []
  (let [datasets (rf/subscribe [:datasets2/list])
        sets (asdf/value @datasets)]
    (when (asdf/should-reload? @datasets)
      (rf/dispatch [:datasets2/load-datasets2]))
    [:div
     (cond
       (nil? sets) [common/loading-placeholder]
       (empty? sets) [no-datasets2-view]
       :else [datasets2-list sets])]))


(defn datasets2-page
  []
  [:article.datasets2
   [listing-updated-datasets]
   (when (db/show-dialog? @(rf/subscribe [:datasets2/view-state]))
     [common/modal-dialog {:on-backdrop-click
                           #(rf/dispatch [:datasets2/cancel-new-dataset])}
      [new-dataset-dialog]])])
