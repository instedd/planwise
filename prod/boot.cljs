(ns prod.boot
  (:require [planwise.client.core :as client]))

;; Ignore (println ...) calls in production mode
(set! *print-fn* (fn [& _]))

(client/init!)
