(ns planwise.client.ui.dialog
  (:require [planwise.client.ui.rmwc :as m]
            [planwise.client.ui.common :as ui]
            [planwise.client.utils :as utils]))

(defn dialog
  [{:keys [open? class title content accept-fn cancel-fn delete-fn acceptable?]}]
  [m/Dialog {:open      open?
             :on-accept accept-fn
             :on-close  cancel-fn
             :className class}
   [m/DialogSurface
    [m/DialogHeader
     [m/DialogHeaderTitle title]
     [ui/close-button {:on-click cancel-fn}]]
    [m/DialogBody
     [:form.vertical {:on-submit (utils/prevent-default accept-fn)}
      content]]
    [m/DialogFooter
     (when (some? delete-fn) [m/Button {:on-click delete-fn} "Delete"])
     (when (some? cancel-fn) [m/DialogFooterButton {:cancel true} "Cancel"])
     (when (some? accept-fn) [m/DialogFooterButton {:accept   true
                                                    :disabled (not acceptable?)} "OK"])]]])
