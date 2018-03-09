(ns planwise.client.datasets2.components.new-dataset
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.asdf :as asdf]
            [planwise.client.config :as config]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]
            [planwise.client.datasets2.db :as db]))


(defn new-dataset-dialog []
  (let [name (r/atom "")
        js-file (r/atom nil)]
    (fn []
      [:div
       [:input {:type "text"
                :value @name
                :placeholder "Enter dataset name..."
                :on-change #(reset! name (-> % .-target .-value))}]
       [:input {:class "none"
                :id "file-upload"
                :type "file"
                :on-change  #(reset! js-file
                                  (-> (.-currentTarget %) .-files (aget 0)))}]
       [:button.primary
          {:on-click #(dispatch [:datasets2/create-load-dataset @name @js-file])}
          "Create"]])))
