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
      (let [items (asdf/value @datasets)]
          (dispatch [:datasets2/load-datasets2])
        [:div
        ;  (cond
        ;    (empty? items) [no-datasets2-view]
          [datasets2-list items]]))))

(defn datasets2-page
  []
  [:article.datasets2
   [:div
      [new-dataset-dialog]
      [(listing-updated-datasets)]]])
