(ns planwise.client.datasets2.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.asdf :as asdf]
            [planwise.client.datasets2.components.listing :refer [no-datasets2-view datasets2-list]]
            [planwise.client.datasets2.components.new-dataset :refer [new-dataset-dialog]]
            [planwise.client.components.common :as common]))

;; ----------------------------------------------------------------------------
;; Datasets2 list

(defn- listing-updated-datasets []
  (let [datasets (subscribe [:datasets2/list])]
    (fn []
      (let [sets (asdf/value @datasets)]
          (dispatch [:datasets2/load-datasets2])
        [:div
         (cond
           (nil? sets) [common/loading-placeholder]
           (empty? sets) [no-datasets2-view]
            :else [datasets2-list sets])]))))


(defn datasets2-page
  []
  [:article.datasets2
   [:div
      [listing-updated-datasets]
      [new-dataset-dialog]]])
