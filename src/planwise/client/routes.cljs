(ns planwise.client.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:require [re-frame.core :refer [dispatch]]
            [secretary.core]))

;; -------------------------
;; Routes

(defroute home "/" []
  (dispatch [:navigate {:page :home}]))
(defroute providers-set "/providers" []
  (dispatch [:navigate {:page :providers-set}]))
(defroute sources "/sources" []
  (dispatch [:navigate {:page :sources}]))
(defroute projects2 "/projects2" []
  (dispatch [:navigate {:page :projects2, :section :index}]))
(defroute projects2-show "/projects2/:id" [id]
  (dispatch [:navigate {:page :projects2, :id (js/parseInt id), :section :show}]))
(defroute design "/_design" []
  (dispatch [:navigate {:page :design}]))
(defroute design-section "/_design/:section" [section query-params]
  (dispatch [:navigate {:page :design, :section (keyword section), :query-params query-params}]))
(defroute download-sample "/sample.csv" [])
(defroute scenarios "/projects2/:project-id/scenarios/:id" [project-id id]
  (dispatch [:navigate {:page :scenarios, :id (js/parseInt id), :project-id (js/parseInt project-id)}]))
(defroute projects2-scenarios "/projects2/:id/scenarios" [id]
  (dispatch [:navigate {:page :projects2, :id (js/parseInt id), :section :project-scenarios}]))
(defroute projects2-settings "/projects2/:id/settings" [id]
  (dispatch [:navigate {:page :projects2, :id (js/parseInt id), :section :project-settings}]))
