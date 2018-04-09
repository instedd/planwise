(ns planwise.client.scenarios.dialog
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.scenarios.db :as db]
            [planwise.client.routes :as routes]
            [clojure.string :as str]
            [planwise.client.ui.rmwc :as m]))

(defn- changing-name
  [{:keys [value]}]
  [:form.vertical
   [m/TextField {:label "Name"
                 :value value
                 :on-change #(rf/dispatch [:scenarios/save-key
                                           [:rename-dialog :value] (-> % .-target .-value)])}]])
(defn new-dialog
  []
  (let [view-state (rf/subscribe [:scenarios/view-state])
        current-scenario (rf/subscribe [:scenarios/current-scenario])
        rename-dialog (rf/subscribe [:scenarios/rename-dialog])
        open?      (db/show-dialog? @view-state)
        accept-fn  #(rf/dispatch [:scenarios/update-scenario %])
        cancel-fn  #(rf/dispatch [:scenarios/cancel-dialog])]
    [m/Dialog {:open open?
               :on-accept accept-fn
               :on-close cancel-fn}
     [m/DialogSurface
      [m/DialogHeader
       [m/DialogHeaderTitle ""]]
      [m/DialogBody
       (changing-name {:value (:value @rename-dialog)})]
      [m/DialogFooter
       [m/DialogFooterButton
        {:cancel true}
        "Cancel"]
       [m/DialogFooterButton
        {:accept true}
        "OK"]]]]))
