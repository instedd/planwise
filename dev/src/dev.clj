(ns dev
  (:refer-clojure :exclude [test])
  (:require [clojure.repl :refer :all]
            [fipp.edn :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [duct.core :as duct]
            [duct.core.repl :as duct-repl]
            [duct.repl.figwheel :refer [cljs-repl]]
            [eftest.runner :as eftest]
            [integrant.core :as ig]
            [integrant.repl :refer [clear halt go init prep reset]]
            [integrant.repl.state :refer [config system]]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [planwise.repl :refer :all]))

(duct/load-hierarchy)

;; Logging configuration for development
(timbre/merge-config! {:level :debug
                       :ns-blacklist ["com.zaxxer.hikari.*"
                                      "org.apache.http.*"
                                      "org.eclipse.jetty.*"]})

;; ;; Activate Schema validation for *all* functions
;; (s/set-fn-validation! true)

(defn read-config []
  (duct/read-config (io/resource "dev.edn")))

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")

(when (io/resource "local.clj")
  (load "local"))

(integrant.repl/set-prep! (comp duct/prep read-config))
