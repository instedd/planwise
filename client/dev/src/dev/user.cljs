(ns dev.user
  (:require [schema.core :as s]
            [planwise.client.core :as client]))

;; Enable Schema validations client-side
(s/set-fn-validation! true)

(js/console.info "Starting in development mode")

(enable-console-print!)

(defn ^:dev/after-load remount
  []
  (client/mount-root))
