(ns planwise.client.providers-set.views
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.providers-set.db :as db]
            [planwise.client.providers-set.components.new-provider-set :refer [new-provider-set-dialog]]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.dialog :refer [dialog]]
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


(defn delete-provider-set-dialog
  [selected-provider-set]
  [dialog {:open? (some? selected-provider-set)
           :title (str "Delete " (:name selected-provider-set))
           :delete-fn #(rf/dispatch [:providers-set/delete-provider-set])
           :cancel-fn #(rf/dispatch [:providers-set/close-delete-dialog])
           :content [:p "Do you want to delete this provider set?"]}])

(defn provider-set-card
  [props {:keys [depending-projects] :as provider-set}]
  (let [name (:name provider-set)
        provider-count (:provider-count provider-set 0)
        no-projects? (zero? depending-projects)]
    [ui/card (merge {:title name}
                    (if no-projects?
                      {:subtitles [(utils/pluralize provider-count "provider")]
                       :action-button [m/Button {:on-click #(rf/dispatch [:providers-set/confirm-delete-provider-set provider-set])} "Delete"]}
                      {:subtitles [(utils/pluralize provider-count "provider")
                                   (str (utils/pluralize depending-projects "project") " depends on this set")]}))]))

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
        sets          (asdf/value @providers-set)
        selected-provider-set (rf/subscribe [:providers-set/delete-selected-provider-set])
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
       [new-provider-set-dialog]
       [delete-provider-set-dialog @selected-provider-set]])))
