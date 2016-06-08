(ns planwise.client.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [secretary.core :as secretary]
            [accountant.core :as accountant]

            [planwise.client.handlers]
            [planwise.client.subs]
            [planwise.client.views :as views]))


;; -------------------------
;; Routes

(defroute "/" [] (dispatch [:navigate :home]))
(defroute "/playground" [] (dispatch [:navigate :playground]))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [views/planwise-app]
                  (.getElementById js/document "app")))

(defn- ^:export main []
  (dispatch-sync [:initialise-db])
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
