(ns prod.boot)

;; Ignore (println ...) calls in production mode
(set! *print-fn* (fn [& _]))
