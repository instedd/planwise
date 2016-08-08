(ns planwise.component.runner
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [clojure.java.shell :refer [sh]]))

(timbre/refer-timbre)

(defn- path-for
  [service kind name]
  (apply str (kind service) name))

(defn run-external
  [service kind name & args]
  (let [sh-args  (cons (path-for service kind name) args)
        _        (info "Invoking " (str/join " " sh-args))
        response (apply sh sh-args)]
    (case (:exit response)
      0 (:out response)
      (throw (RuntimeException. (str "Error running `" (str/join " " sh-args) "`:\n" (:err response)))))))

(defrecord RunnerService [config])

(defn runner-service
  "Construct a Runner Service component"
  [config]
  (map->RunnerService config))
