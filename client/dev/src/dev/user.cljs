(ns dev.user
  (:require [schema.core :as s]
            [re-frame.core :as rf]
            [planwise.client.core :as client]))

;; Enable Schema validations client-side
(s/set-fn-validation! true)

(js/console.info "Starting in development mode")

(enable-console-print!)

(defn ^:dev/after-load remount
  []
  (rf/clear-subscription-cache!)
  (client/mount-root))
