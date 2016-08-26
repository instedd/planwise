(ns planwise.client.datasets.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common :as common]
            [planwise.client.datasets.components.listing :refer [no-datasets-view datasets-list]]
            [planwise.client.datasets.components.new-dataset :refer [new-dataset-dialog]]
            [planwise.client.datasets.db :as db]))


;; ----------------------------------------------------------------------------
;; Datasets list

(defn datasets-page
  []
  (let [state (subscribe [:datasets/state])
        datasets (subscribe [:datasets/list])
        filtered-datasets (subscribe [:datasets/filtered-list])]
    (fn []
      (let [sets (asdf/value @datasets)]
        (when (asdf/should-reload? @datasets)
          (dispatch [:datasets/load-datasets]))
        [:article.datasets
         (cond
           (nil? sets) [common/loading-placeholder]
           (empty? sets) [no-datasets-view]
           :else [datasets-list @filtered-datasets])
         (when (db/show-dialog? @state)
           [common/modal-dialog {:on-backdrop-click
                                 #(dispatch [:datasets/cancel-new-dataset])}
            [new-dataset-dialog]])]))))
