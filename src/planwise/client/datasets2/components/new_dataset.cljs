(ns planwise.client.datasets2.components.new-dataset
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.datasets2.db :as db]
            [clojure.string :as str]
            [planwise.client.ui.rmwc :as m]))


(defn new-dataset-dialog
  []
  (let [name       (r/atom "")
        js-file    (r/atom nil)
        coverage   (r/atom nil)
        algorithms (rf/subscribe [:coverage/algorithms-list])]
    (fn []
      (let [view-state (rf/subscribe [:datasets2/view-state])
            open?      (db/show-dialog? @view-state)
            disabled?  (or (= @view-state :creating)
                           (str/blank? @name)
                           (nil? @js-file)
                           (nil? @coverage))
            accept-fn  #(rf/dispatch [:datasets2/create-load-dataset
                                      {:name @name
                                       :csv-file @js-file
                                       :coverage-algorithm @coverage}])
            cancel-fn  #(rf/dispatch [:datasets2/cancel-new-dataset])
            key-handler-fn #(case (.-which %) 27 (cancel-fn) nil)]
        [m/Dialog {:open open?
                   :on-accept accept-fn
                   :on-close cancel-fn}
         [m/DialogSurface
          [m/DialogHeader
           [m/DialogHeaderTitle "New dataset"]]
          [m/DialogBody
           [:form.vertical
            [m/TextField {:label "Name"
                          :value @name
                          :on-change #(reset! name (-> % .-target .-value))}]
            [:label.file-input-wrapper
             [:div "Import sites from CSV"]
             [:input {:id "file-upload"
                      :type "file"
                      :on-change  #(reset! js-file
                                           (-> (.-currentTarget %) .-files (aget 0)))}]]
            [m/Select {:label "Coverage algorithm"
                       :value @coverage
                       :options @algorithms
                       :on-change #(reset! coverage (-> % .-target .-value))}]

            (when-let [last-error @(rf/subscribe [:datasets2/last-error])]
              [:div.error-message
               (str last-error)])]]
          [m/DialogFooter
           [m/DialogFooterButton
            {:cancel true}
            "Cancel"]
           [m/DialogFooterButton
            {:accept true :disabled disabled?}
            (if (= @view-state :creating) "Creating..." "Create")]]]]))))
