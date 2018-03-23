(ns planwise.client.datasets2.components.dropdown
    (:require [re-frame.core :refer [subscribe dispatch]]
              [reagent.core :as r]
              [clojure.string :as string]
              [planwise.client.utils :as utils]
              [planwise.client.components.common2 :as common2]
              [planwise.client.datasets2.db :as db]
              [planwise.client.ui.rmwc :as m]
              [re-frame.utils :as c]))

(defn datasets-dropdown-component
  []
  (let [datasets-list (subscribe [:datasets2/list])]
    (fn []
        (do
          (dispatch [:datasets2/load-datasets2])
          [m/Select {:label (if (empty? @datasets-list) "This projects has no datasets yet." "Sites")
                     :disabled (empty? @datasets-list)
                     :options @datasets-list
                     :on-change #(dispatch [:projects2/save-key :dataset-id (js/parseInt (-> % .-target .-value))])
                     }]
                     ))))


