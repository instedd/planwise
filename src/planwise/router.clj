(ns planwise.router
  (:require [integrant.core :as ig]
            [compojure.core :as compojure]))

(defn make-router
  [{:keys [handlers middleware]}]
  (apply compojure/routes handlers))

(defmethod ig/init-key :planwise.router/api
  [_ config]
  (make-router config))

(defmethod ig/init-key :planwise.router/app
  [_ config]
  (make-router config))
