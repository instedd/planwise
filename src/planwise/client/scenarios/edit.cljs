(ns planwise.client.scenarios.edit
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.ui.common :as ui]
            [planwise.client.scenarios.db :as db]
            [planwise.client.utils :as utils]
            [planwise.client.routes :as routes]
            [clojure.string :as str]
            [planwise.client.ui.rmwc :as m]))

(defn new-dialog
  [{:keys [open? title content accept-fn cancel-fn]}]
  [m/Dialog {:open open?
             :on-accept accept-fn
             :on-close cancel-fn}
   [m/DialogSurface
    [m/DialogHeader
     [m/DialogHeaderTitle title]]
    [m/DialogBody
     [:form.vertical {:on-submit (utils/prevent-default accept-fn)}
      content]]
    [m/DialogFooter
     [m/DialogFooterButton
      {:cancel true}
      "Cancel"]
     [m/DialogFooterButton
      {:accept true}
      "OK"]]]])

(defn rename-button []
  [m/Button {:id "edit-name-dialog"
             :on-click #(dispatch [:scenarios/open-rename-dialog])}
   "Edit name"])

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

(defn add-site-button []
  [m/Fab {:id "add-site"
          :class "MyClass"
          :on-click #(dispatch [:scenarios/adding-new-site])} "star"])

(defn site-button
  [site]
  [m/Button {:id "edit-site-dialog"
             :on-click #(dispatch [:scenarios/open-site-dialog site])}
   "Edit Site"])

(defn input
  [{:keys [value change-fn]}]
  [m/TextField {:type "text"
                :on-change  #(-> % .-target .-value change-fn)
                :value value}])

(defn site-dialog-content
  [{:keys [investment capacity]}]
  [:div
   [:h2 "Investment"]
   [input {:value investment
           :on-change #(dispatch [:scenarios/save-key [:site :investment] %])}]
   [:h2 "Capacity"]
   [input {:value investment
           :on-change #(dispatch [:scenarios/save-key [:site :capacity] %])}]])

(defn site-dialog
  []
  (let [site (subscribe [:scenarios/edit-site])
        view-state (subscribe [:scenarios/view-state])]
    (fn []
      (new-dialog {:open? (= @view-state :edit-site)
                   :title (str "Edit site " (:id @site))
                   :content (site-dialog-content @site)
                   :accept-fn  #(dispatch [:scenarios/accept-site-dialog])
                   :cancel-fn  #(dispatch [:scenarios/cancel-site-dialog])}))))

