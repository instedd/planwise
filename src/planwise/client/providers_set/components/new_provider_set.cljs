(ns planwise.client.providers-set.components.new-provider-set
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.components.common2 :as common2]
            [planwise.client.providers-set.db :as db]
            [planwise.client.routes :as routes]
            [clojure.string :as str]
            [planwise.client.ui.rmwc :as m]))


(defn new-provider-set-dialog
  []
  (let [algorithms (rf/subscribe [:coverage/algorithms-list])
        view-state (rf/subscribe [:providers-set/view-state])
        open?      (db/show-dialog? @view-state)
        name       (rf/subscribe [:providers-set/new-provider-set-name])
        js-file    (rf/subscribe [:providers-set/new-provider-set-js-file])
        coverage   (rf/subscribe [:providers-set/new-provider-set-coverage])
        disabled?  (or (= @view-state :creating)
                       (str/blank? @name)
                       (nil? @js-file)
                       (nil? @coverage))
        accept-fn  #(rf/dispatch [:providers-set/create-load-provider-set
                                  {:name @name
                                   :csv-file @js-file
                                   :coverage-algorithm @coverage}])
        cancel-fn  #(rf/dispatch [:providers-set/cancel-new-provider-set])
        key-handler-fn #(case (.-which %) 27 (cancel-fn) nil)]
    [m/Dialog {:open open?
               :on-accept accept-fn
               :on-close cancel-fn}
     [m/DialogSurface
      [m/DialogHeader
       [m/DialogHeaderTitle "New Providers List"]]
      [m/DialogBody
       [:form.vertical
        [common2/text-field {:label "Name"
                             :value @name
                             :on-change #(rf/dispatch [:providers-set/new-provider-set-update
                                                       :name (-> % .-target .-value)])}]
        [:label.file-input-wrapper
         [:div "Import providers from CSV"]
         [:input {:id "file-upload"
                  :type "file"
                  :class "file-input"
                  :value ""
                  :on-change  #(rf/dispatch [:providers-set/new-provider-set-update
                                             :js-file (-> (.-currentTarget %) .-files (aget 0))])}]
         (when (some? @js-file)
           [:span (.-name @js-file)])]
        [:a {:href (routes/download-providers-sample)
             :data-trigger "false"} "Download a sample providers list"]
        [m/Select {:label "Coverage algorithm"
                   :value @coverage
                   :options @algorithms
                   :on-change #(rf/dispatch [:providers-set/new-provider-set-update
                                             :coverage (-> % .-target .-value)])}]

        (when-let [last-error @(rf/subscribe [:providers-set/last-error])]
          [:div.error-message
           (str last-error)])]]
      [m/DialogFooter
       [m/DialogFooterButton
        {:cancel true}
        "Cancel"]
       [m/DialogFooterButton
        {:accept true :disabled disabled?}
        (if (= @view-state :creating) "Creating..." "Create")]]]]))
