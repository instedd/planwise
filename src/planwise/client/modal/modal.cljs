(ns planwise.client.modal.modal
  (:require [re-frame.core :as rf]
            [planwise.client.ui.common :as ui]
            [planwise.client.components.common2 :as common2]
            [planwise.client.ui.rmwc :as m]))

(def in-modal-path (rf/path [:modal-window]))

;; ----------------------------------------------------------------------------
;; Subscription
(rf/reg-sub
  :modal/config
  (fn [db _]
    (get-in db [:modal-window :config])))

;; ----------------------------------------------------------------------------
;; Handlers
(rf/reg-event-db
  :modal/show
  in-modal-path
  (fn [db [_ config]]
    (assoc-in db [:config] config)))

;; ----------------------------------------------------------------------------
;; View
(defn modal-view
  [content]
  (fn []
    (let [config @(rf/subscribe [:modal/config])]
      [m/Dialog {:open (:open? config)
                 :on-accept (:accept-fn config)
                 :on-close (:cancel-fn config)}
       [m/DialogSurface
        [m/DialogHeader
         [m/DialogHeaderTitle (:title config)]]
        [m/DialogBody content]
        [m/DialogFooter
         [m/DialogFooterButton
          {:cancel true}
          "Cancel"]
         [m/DialogFooterButton
          {:accept true :disabled (:accept-disabled config)}
          (:accept-label config)]]]])))
