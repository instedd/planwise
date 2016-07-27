(ns planwise.client.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [secretary.core :as secretary]
            [accountant.core :as accountant]

            [planwise.client.routes]
            [planwise.client.handlers]
            [planwise.client.subs]
            [planwise.client.views :as views]))


;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [views/planwise-app]
                  (.getElementById js/document "app")))

(defn install-message-handler! []
  (.addEventListener js/window
                     "message"
                     (fn [e]
                       (let [message (.-data e)]
                         (dispatch [:message-posted message])))))

(defn install-ticker! []
  (let [time (atom 0)
        interval 1000]
    (letfn [(timeout-fn [] (.setTimeout
                            js/window
                            #(do
                              (dispatch [:tick (swap! time + interval)])
                              (timeout-fn))
                            interval))]
      (timeout-fn))))

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
  (install-message-handler!)
  (install-ticker!)
  (mount-root))
