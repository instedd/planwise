(ns prod.boot
  (:require [re-frame.utils :as c]))

;; Ignore (println ...) calls in production mode
(set! *print-fn* (fn [& _]))

;; Change re-frame's default loggers
(c/set-loggers! (merge c/default-loggers
                       {:log  (fn [& _])}))
