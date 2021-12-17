(ns planwise.client.ui.dialog
  (:require [reagent.core :as r]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.ui.common :as ui]
            [planwise.client.utils :as utils]))

(defn dialog
  [{:keys [open? class title content accept-fn cancel-fn delete-fn acceptable?]}]
  (r/with-let []
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
       (when (some? delete-fn)
         [:div {:class (when (some? accept-fn) "flex-spacer")}
          [m/Button {:on-click delete-fn} "Delete"]])
       (when (some? cancel-fn) [m/DialogFooterButton {:cancel true} "Cancel"])
       (when (some? accept-fn) [m/DialogFooterButton {:accept     true
                                                      :unelevated true
                                                      :disabled   (not acceptable?)} "OK"])]]]
    (finally
      ;; NB. this is a hack/fix to avoid the scroll lock when a RMWC Dialog is
      ;; destroyed while running the hide transition. In that case the HTML body
      ;; had the `mdc-dialog-scroll-lock` class added because the dialog was
      ;; open, but MDC fails to remove it if the dialog is destroyed before the
      ;; transition end for the closing animation event occurs
      (js/document.body.classList.remove "mdc-dialog-scroll-lock"))))
