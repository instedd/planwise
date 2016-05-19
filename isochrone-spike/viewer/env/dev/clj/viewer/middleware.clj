(ns viewer.middleware
  (:require [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]))

(defn wrap-middleware [handler]
  (-> handler
      wrap-exceptions
      wrap-reload))
