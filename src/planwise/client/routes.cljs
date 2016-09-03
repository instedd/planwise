(ns planwise.client.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:require [re-frame.core :refer [dispatch]]
            [secretary.core]))

;; -------------------------
;; Routes

(defroute home "/" []
  (dispatch [:navigate {:page :home}]))
(defroute project-demographics "/projects/:id" [id]
  (dispatch [:navigate {:page :projects, :id id, :section :demographics}]))
(defroute project-facilities "/projects/:id/facilities" [id]
  (dispatch [:navigate {:page :projects, :id id, :section :facilities}]))
(defroute project-transport "/projects/:id/transport" [id]
  (dispatch [:navigate {:page :projects, :id id, :section :transport}]))
(defroute project-scenarios "/projects/:id/scenarios" [id]
  (dispatch [:navigate {:page :projects, :id id, :section :scenarios}]))
(defroute project-access "/projects/:id/access/:token" [id token]
  (dispatch [:navigate {:page :projects, :id id, :section :access, :token token}]))
(defroute datasets "/datasets" []
  (dispatch [:navigate {:page :datasets}]))
