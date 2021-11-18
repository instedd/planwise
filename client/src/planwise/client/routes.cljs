(ns planwise.client.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:require [re-frame.core :refer [dispatch] :as rf]
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
(defroute projects2-new "/projects2/new" []
  (dispatch [:navigate {:page :projects2, :section :new}]))
(defroute projects2-show "/projects2/:id" [id]
  (dispatch [:navigate {:page :projects2, :id (js/parseInt id), :section :show}]))
(defroute projects2-show-with-step "/projects2/:id/steps/:step" [id step]
  (dispatch [:navigate {:page :projects2, :id (js/parseInt id), :step step, :section :show}]))
(defroute projects2-scenarios "/projects2/:id/scenarios" [id]
  (dispatch [:navigate {:page :projects2, :id (js/parseInt id), :section :project-scenarios}]))
(defroute projects2-settings "/projects2/:id/settings" [id]
  (dispatch [:navigate {:page :projects2, :id (js/parseInt id), :section :project-settings}]))
(defroute projects2-settings-with-step "/projects2/:id/settings/:step" [id step]
  (dispatch [:navigate {:page :projects2, :id (js/parseInt id), :step step, :section :project-settings}]))

(defroute scenarios "/projects2/:project-id/scenarios/:id" [project-id id]
  (dispatch [:navigate {:page :scenarios, :id (js/parseInt id), :project-id (js/parseInt project-id)}]))

(defroute download-providers-sample "/providers-sample.csv" [])
(defroute download-sources-sample "/sources-sample.csv" [])


;; Controllers
;; Roughly copied from kee-frame https://github.com/ingesolvoll/kee-frame

(defonce +controllers+ (atom {}))

(defn reg-controller
  [controller]
  (println "Registering controller " (:id controller))
  (swap! +controllers+ update (:id controller) merge controller))

(defn- start!
  [{:keys [id start]} state]
  (println (str "Starting " id " with " state))
  (when start
    (dispatch (conj start state))))

(defn- stop!
  [{:keys [id stop]} state]
  (println (str "Stopping " id " with " state))
  (when stop
    (dispatch (conj stop state))))

(defn- process-controller
  [{:keys [params->state last-state] :as controller} params]
  (let [state (params->state params)]
    (cond
      (= state last-state)                  nil
      (and (nil? last-state) (some? state)) (start! controller state)
      (and (some? last-state) (nil? state)) (stop! controller last-state)
      :else                                 (do
                                              (stop! controller last-state)
                                              (start! controller state)))
    (assoc controller :last-state state)))

(defn- process-route-change
  [controllers params]
  (->> controllers
       (map (fn [[id controller]]
              [id (process-controller controller params)]))
       (into {})))

(rf/reg-fx
 :route-change
 (fn [params]
   (println "Processing route change " params)
   (swap! +controllers+ process-route-change params)))
