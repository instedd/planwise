(ns planwise.client.datasets2.components.new-dataset
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.asdf :as asdf]
            [planwise.client.datasets2.components.uploading-csv-file :as up-csv]
            [planwise.client.config :as config]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]
            [planwise.client.datasets2.db :as db]))

(defn- update-atom
  [value]
  [:input {:type "text"
           :value @value
           :placeholder "Enter dataset name..."
           :on-change #(reset! value (-> % .-target .-value))}])


(defn- create-button [atom]
  [:button.primary
    {:on-click #(dispatch [:datasets2/create-dataset @atom])}
    "Create"])

(defn- upload-button []
  (fn []
    [:input {:class "none"
             :id "file-upload"
             :type "file"
             :on-change #(let [element (.-currentTarget %)
                               js-file (-> element .-files (aget 0))]
                              (dispatch [:datasets2/load-sites js-file]))}]))


; (defn- create-upload-component [atom]
;   [:div
;     [(fn []
;        [:input {:class "none"
;                 :id "file-upload"
;                 :type "file"
;                 :on-change #(let [js-file (r/atom "")
;                                   element (.-currentTarget %)]
;                               (swap! js-file  (-> element .-files (aget 0))))}]
;
;        [:button.primary
;          {:on-click #(dispatch [:datasets2/create-load-dataset @atom @js-file])}
;          "Create"])]])

(defn new-dataset-dialog []
  (let [name (r/atom "")]
    (fn [])
    [:div
      [:div.search-box
       [update-atom name]
       [upload-button]]]))
