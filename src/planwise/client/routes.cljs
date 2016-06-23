(ns planwise.client.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:require [re-frame.core :refer [dispatch]]
            [secretary.core]))

;; -------------------------
;; Routes

(defroute home "/" []
  (dispatch [:navigate :home]))
(defroute playground "/playground" []
  (dispatch [:navigate :playground]))
(defroute project-demographics "/projects/:id" [id]
  (dispatch [:navigate :projects id :demographics]))
(defroute project-facilities "/projects/:id/facilities" [id]
  (dispatch [:navigate :projects id :facilities]))
(defroute project-transport "/projects/:id/transport" [id]
  (dispatch [:navigate :projects id :transport]))
(defroute project-scenarios "/projects/:id/scenarios" [id]
  (dispatch [:navigate :projects id :scenarios]))
