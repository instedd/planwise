(ns planwise.client.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [accountant.core :as accountant]

            [planwise.client.effects]
            [planwise.client.routes]
            [planwise.client.handlers]
            [planwise.client.subs]
            [planwise.client.views :as views]))


;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [views/planwise-app]
                  (.getElementById js/document "app")))

(defn- ^:export main []
  (rf/dispatch-sync [:initialise-db])
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (secretary/dispatch! path))
    :path-exists?
    (fn [path]
      (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
