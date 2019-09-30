(ns dev
  (:refer-clojure :exclude [test])
  (:require [clojure.repl :refer :all]
            [fipp.edn :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [duct.core :as duct]
            [duct.core.repl :as duct-repl]
            [eftest.runner :as eftest]
            [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [integrant.repl :refer [clear halt init prep]]
            [integrant.repl.state :refer [config system]]
            [taoensso.timbre :as timbre]
            [planwise.config]
            [planwise.repl :refer :all]
            [planwise.virgil]))

(duct/load-hierarchy)

;; Logging configuration for development
(timbre/merge-config! {:level :debug
                       :ns-blacklist ["com.zaxxer.hikari.*"
                                      "org.apache.http.*"
                                      "org.eclipse.jetty.*"
                                      "org.openid4java.*"]})

(defn read-config []
  (duct/read-config (io/resource "dev.edn")))

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")
(planwise.virgil/set-java-source-dirs! ["java"])

(when (io/resource "local.clj")
  (load "local"))

(integrant.repl/set-prep! (comp duct/prep read-config))

(s/check-asserts true)
