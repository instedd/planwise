(ns planwise.client.datasets.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :refer [capitalize]]
            [re-com.core :as rc]
            [planwise.client.config :as config]
            [planwise.client.components.common :as common]
            [planwise.client.datasets.components.listing :refer [no-datasets-view datasets-list]]
            [planwise.client.datasets.components.new-dataset :refer [new-dataset-dialog]]
            [planwise.client.datasets.db :as db]
            [planwise.client.utils :as utils]))


;; ----------------------------------------------------------------------------
;; Datasets list

(defn datasets-page
  []
  (let [view-state (subscribe [:datasets/view-state])
        datasets (subscribe [:datasets/list])
        filtered-datasets (subscribe [:datasets/filtered-list])]
    (fn []
      (dispatch [:datasets/load-datasets])
      [:article.datasets
       (cond
         (nil? @datasets) [common/loading-placeholder]
         (empty? @datasets) [no-datasets-view]
         :else [datasets-list @filtered-datasets])
       (when (db/show-dialog? @view-state)
         [common/modal-dialog {:on-backdrop-click
                               #(dispatch [:datasets/cancel-new-dataset])}
          [new-dataset-dialog]])])))
