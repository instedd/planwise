(ns planwise.client.scenarios.edit
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.dialog :refer [new-dialog]]
            [planwise.client.ui.common :as ui]
            [planwise.client.scenarios.db :as db]
            [planwise.client.utils :as utils]
            [planwise.client.routes :as routes]
            [clojure.string :as str]
            [planwise.client.ui.rmwc :as m]))

(defn- valid-input
  [inp]
  (let [value (js/parseInt inp)]
    (if (and (number? value) (not (js/isNaN value))) value nil)))

(defn rename-scenario-dialog
  []
  (let [rename-dialog (subscribe [:scenarios/rename-dialog])
        view-state (subscribe [:scenarios/view-state])]
    (fn []
      (new-dialog {:open? (= @view-state :rename-dialog)
                   :title "Rename scenario"
                   :content  [m/TextField {:label "Name"
                                           :value (str (:value @rename-dialog))
                                           :on-change #(dispatch [:scenarios/save-key
                                                                  [:rename-dialog :value] (-> % .-target .-value)])}]
                   :accept-fn  #(dispatch [:scenarios/accept-rename-dialog])
                   :cancel-fn  #(dispatch [:scenarios/cancel-rename-dialog])}))))

(defn input
  [{:keys [value onchange-path]}]
  [m/TextField {:type "text"
                :on-change  #(dispatch [:scenarios/save-key onchange-path (valid-input (-> % .-target .-value))])
                :value value}])

(defn changeset-dialog-content
  [{:keys [investment capacity]}]
  [:div
   [:h2 "Investment"]
   [input {:value (or investment "")
           :onchange-path [:changeset-dialog :investment]}]
   [:h2 "Capacity"]
   [input {:value (or capacity "")
           :onchange-path [:changeset-dialog :capacity]}]])

(defn changeset-dialog
  []
  (let [site       (subscribe [:scenarios/changeset-dialog])
        view-state (subscribe [:scenarios/view-state])
        site-index (subscribe [:scenarios/changeset-index])]
    (fn []
      (new-dialog {:open? (= @view-state :changeset-dialog)
                   :title "Edit Site"
                   :content (changeset-dialog-content @site)
                   :delete-fn #(dispatch [:scenarios/delete-site @site-index])
                   :accept-fn  #(dispatch [:scenarios/accept-changeset-dialog])
                   :cancel-fn  #(dispatch [:scenarios/cancel-changeset-dialog])}))))

