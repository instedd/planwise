(ns planwise.component.runner
  (:require [planwise.boundary.runner :as boundary]
            [integrant.core :as ig]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [clojure.java.shell :refer [sh]]))

(timbre/refer-timbre)

(defn- path-for
  [service kind name]
  (if kind
    (if-let [folder (kind service)]
      (apply str (kind service) name)
      (throw (ex-info (str "Path for " kind " not found") {:kind kind})))
    name))

(defn run-external
  [service kind timeout name & args]
  (let [sh-args  (cons (path-for service kind name) args)
        _        (info "Invoking" (str/join " " sh-args))
        response (if timeout
                   (deref (future (apply sh sh-args)) timeout {:exit :timeout, :err :timeout})
                   (apply sh sh-args))]
    (case (:exit response)
      0 (:out response)
      (throw
        (ex-info
          (str "Error running external " kind ": " (:err response))
          {:args args, :code (:exit response), :err (:err response)})))))

(defrecord RunnerService [config]
  boundary/Runner
  (run-external [service kind timeout name args]
    (apply run-external (concat [service kind timeout name] args))))

(defmethod ig/init-key :planwise.component/runner
  [_ config]
  (map->RunnerService config))
