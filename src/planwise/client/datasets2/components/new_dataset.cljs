(ns planwise.client.datasets2.components.new-dataset
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.asdf :as asdf]
            [planwise.client.config :as config]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]
            [planwise.client.datasets2.db :as db]
            [clojure.string :as str]))


(defn new-dataset-dialog []
  (let [name       (r/atom "")
        js-file    (r/atom nil)
        coverage   (r/atom nil)
        algorithms (rf/subscribe [:coverage/algorithms-list])]
    (fn []
      (let [view-state (rf/subscribe [:datasets2/view-state])
            cancel-fn #(rf/dispatch [:datasets2/cancel-new-dataset])
            key-handler-fn #(case (.-which %) 27 (cancel-fn) nil)]
        [:form.dialog
         {:on-submit (utils/prevent-default
                      #(rf/dispatch [:datasets2/create-load-dataset
                                     {:name @name
                                      :csv-file @js-file
                                      :coverage-alg @coverage}]))}
         [:div.title
          [:h1 "New dataset"]
          [common/close-button {:on-click cancel-fn}]]
         [:div.form-control
          [:label {:for "dataset-name"} "Name"]
          [:input#dataset-name {:type "text"
                                :value @name
                                :placeholder "Enter dataset name..."
                                :on-change #(reset! name (-> % .-target .-value))}]]
         [:div.form-control
          [:label {:for "dataset-file"} "Sites CSV file"]
          [:input#dataset-file {:class "none"
                                :id "file-upload"
                                :type "file"
                                :on-change  #(reset! js-file
                                                     (-> (.-currentTarget %) .-files (aget 0)))}]]
         [:div.form-control-2
          [:label {:for "dataset-coverage"} "Coverage algorithm"]
          [rc/single-dropdown
           :choices @algorithms
           :on-change #(reset! coverage %)
           :filter-box? false
           :model @coverage]]

         [:div.actions
          [:button.primary
           {:type "submit"
            :disabled (or (= @view-state :creating)
                          (str/blank? @name)
                          (nil? @js-file)
                          (nil? @coverage))}
           (if (= @view-state :creating)
             "Creating..."
             "Create")]
          [:button.cancel
           {:type "button"
            :on-click cancel-fn}
           "Cancel"]]]))))
