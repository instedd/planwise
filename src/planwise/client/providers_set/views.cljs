(ns planwise.client.providers-set.views
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.providers-set.db :as db]
            [planwise.client.providers-set.components.new-provider-set :refer [new-provider-set-dialog]]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.components.common2 :as common2]
            [planwise.client.components.common :as common]))

;; ----------------------------------------------------------------------------
;; providers-set list

(defn no-providers-set-view
  []
  [:div.empty-list-container
   [:div.empty-list
    [common/icon :box]
    [:p "You have no providers-set yet"]]])

(defn provider-set-card
  [props provider-set]
  (let [name (:name provider-set)
        provider-count (:provider-count provider-set 0)]
    [ui/card {:title name
              :subtitle (utils/pluralize provider-count "provider")
              :action-button [m/Button {} "Delete"]}]))

(defn providers-set-list
  [providers-set]
  (if (empty? providers-set)
    [no-providers-set-view]
    [ui/card-list {:class "set-list"}
     (for [provider-set providers-set]
       [provider-set-card {:key (:id provider-set)} provider-set])]))

(defn providers-set-page
  []
  (let [providers-set (rf/subscribe [:providers-set/list])
        sets (asdf/value @providers-set)
        create-provider-set-button (ui/main-action {:icon "add"
                                                    :on-click #(rf/dispatch [:providers-set/begin-new-provider-set])})]
    (when (asdf/should-reload? @providers-set)
      (rf/dispatch [:providers-set/load-providers-set]))
    (cond
      (nil? sets) [common2/loading-placeholder]
      :else
      [ui/fixed-width (assoc (common2/nav-params)
                             :action create-provider-set-button)
       [providers-set-list sets]
       [new-provider-set-dialog]])))
