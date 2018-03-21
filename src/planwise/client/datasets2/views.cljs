(ns planwise.client.datasets2.views
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
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
  (let [name (:name dataset)]
    [ui/card {:primary [:img {:src "http://via.placeholder.com/373x278"}]
              :title name}]))

(defn datasets2-list
  [datasets]
  [ui/card-list {}
   (for [dataset datasets]
     [dataset2-card {:key (:id dataset)} dataset])])

(defn- listing-updated-datasets
  []
  (let [datasets (rf/subscribe [:datasets2/list])
        sets (asdf/value @datasets)]
    (when (asdf/should-reload? @datasets)
      (rf/dispatch [:datasets2/load-datasets2]))
    [:div
     (cond
       (nil? sets) [common2/loading-placeholder]
       (empty? sets) [no-datasets2-view]
       :else [datasets2-list sets])]))


(defn datasets2-page
  []
  (let [create-dataset-button (ui/main-action
                               {:icon "add"
                                :on-click #(rf/dispatch [:datasets2/begin-new-dataset])})]
    [ui/fixed-width (assoc (common2/nav-params)
                           :action create-dataset-button)
     [listing-updated-datasets]
     [new-dataset-dialog]]))
