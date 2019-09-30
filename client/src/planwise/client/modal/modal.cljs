(ns planwise.client.modal.modal
  (:require [re-frame.core :as rf]
            [planwise.client.ui.common :as ui]
            [planwise.client.components.common2 :as common2]
            [planwise.client.ui.rmwc :as m]))

(def in-modal-path (rf/path [:modal-window]))

;; ----------------------------------------------------------------------------
;; Subscription
(rf/reg-sub
 :modal/state
 (fn [db _]
   (get-in db [:modal-window :state])))

;; ----------------------------------------------------------------------------
;; Handlers
(rf/reg-event-db
 :modal/show
 in-modal-path
 (fn [db _]
   (assoc-in db [:state] {:open? true})))

(rf/reg-event-db
 :modal/hide
 in-modal-path
 (fn [db _]
   (assoc-in db [:state] {:open? false})))

;; ----------------------------------------------------------------------------
;; View
(defn modal-view
  [attrs content]
  (fn [attrs _]
    (let [state @(rf/subscribe [:modal/state])]
      [m/Dialog {:open (:open? state)}
       [m/DialogSurface
        [m/DialogHeader
         [m/DialogHeaderTitle (:title attrs)]]
        [m/DialogBody content]
        [m/DialogFooter
         [m/DialogFooterButton {:on-click (fn [] ((:cancel-fn attrs)))}
          "Cancel"]
         [m/DialogFooterButton {:on-click (fn [] ((:accept-fn attrs)))
                                :disabled (not (:accept-enabled? attrs))}
          (:accept-label attrs)]]]])))
