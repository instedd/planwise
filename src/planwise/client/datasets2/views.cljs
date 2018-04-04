(ns planwise.client.datasets2.views
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.datasets2.db :as db]
            [planwise.client.datasets2.components.new-dataset :refer [new-dataset-dialog]]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.components.common2 :as common2]
            [planwise.client.components.common :as common]))

;; ----------------------------------------------------------------------------
;; Datasets2 list

(defn no-datasets2-view []
  [:div.empty-list
   [common/icon :box]
   [:p "You have no datasets yet"]])

(defn dataset2-card
  [props dataset]
  (let [name (:name dataset)
        site-count (:site-count dataset 0)]
    [ui/card {:title name
              :subtitle (utils/pluralize site-count "site")}]))

(defn datasets2-list
  [datasets]
  (cond
    (empty? datasets) [no-datasets2-view]
    :else
    [ui/card-list {:class "dataset-list"}
     (for [dataset datasets]
       [dataset2-card {:key (:id dataset)} dataset])]))

(defn datasets2-page
  []
  (let [datasets (rf/subscribe [:datasets2/list])
        sets (asdf/value @datasets)
        create-dataset-button (ui/main-action {:icon "add"
                                               :on-click #(rf/dispatch [:datasets2/begin-new-dataset])})]
    (when (asdf/should-reload? @datasets)
      (rf/dispatch [:datasets2/load-datasets2]))
    (cond
      (nil? sets) [common2/loading-placeholder]
      :else
      [ui/fixed-width (assoc (common2/nav-params)
                             :action create-dataset-button)
       [datasets2-list sets]
       [new-dataset-dialog]])))
