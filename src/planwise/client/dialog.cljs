(ns planwise.client.dialog
  (:require [planwise.client.ui.rmwc :as m]
            [re-frame.core :refer [dispatch]]
            [planwise.client.utils :as utils]))

(defn new-dialog
  [{:keys [open? title content delete-dispatch accept-fn cancel-fn delete-fn]}]
  [m/Dialog {:open open?
             :on-accept (or  accept-fn #(dispatch [:dialog/delay-dispatch delete-dispatch]))
             :on-close cancel-fn}
   [m/DialogSurface
    [m/DialogHeader
     [m/DialogHeaderTitle title]]
    [m/DialogBody
     [:form.vertical {:on-submit (utils/prevent-default accept-fn)}
      content]]
    [m/DialogFooter
     (when (some? delete-fn) [m/Button {:on-click delete-fn} "Delete"])
     [m/DialogFooterButton
      {:cancel true}
      "Cancel"]
     [m/DialogFooterButton
      {:accept true}
      "OK"]]]])