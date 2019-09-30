(ns planwise.client.dialog
  (:require [planwise.client.ui.rmwc :as m]
            [planwise.client.utils :as utils]))

(defn dialog
  [{:keys [open? title content accept-fn cancel-fn delete-fn acceptable?]}]
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
     (when (some? delete-fn) [m/Button {:on-click delete-fn} "Delete"])
     (when (some? cancel-fn) [m/DialogFooterButton {:cancel true} "Cancel"])
     (when (some? accept-fn) [m/DialogFooterButton {:accept true
                                                    :disabled (not acceptable?)} "OK"])]]])
