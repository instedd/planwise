(ns planwise.client.scenarios.edit
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.scenarios.db :as db]
            [planwise.client.utils :as utils]
            [planwise.client.routes :as routes]
            [clojure.string :as str]
            [planwise.client.ui.rmwc :as m]))

(defn new-dialog
  [{:keys [open? title content accept-fn cancel-fn]}]
  (let [current-scenario (subscribe [:scenarios/current-scenario])]
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
        "OK"]]]]))

(defn rename-button []
  [m/Button {:id "edit-name-dialog"
             :on-click #(dispatch [:scenarios/open-rename-dialog])}
   "Edit name"])

(defn rename-scenario-dialog
  []
  (let [rename-dialog (subscribe [:scenarios/rename-dialog])
        view-state (subscribe [:scenarios/view-state])]
    (new-dialog {:open? (and (some? @rename-dialog) (= @view-state :rename-dialog))
                 :title "Rename scenario"
                 :content  [m/TextField {:label "Name"
                                         :value (str (:value @rename-dialog))
                                         :on-change #(dispatch [:scenarios/save-key
                                                                [:rename-dialog :value] (-> % .-target .-value)])}]
                 :accept-fn  #(dispatch [:scenarios/accept-rename-dialog])
                 :cancel-fn  #(dispatch [:scenarios/cancel-rename-dialog])})))