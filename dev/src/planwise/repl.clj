(ns planwise.repl
  (:refer-clojure :exclude [test])
  (:require [integrant.core :as ig]
            [integrant.repl :as igr]
            [integrant.repl.state :refer [config system]]
            [duct.server.figwheel :as figwheel]
            [figwheel-sidecar.utils :as fig-utils]
            [eftest.runner :as eftest]
            [planwise.database :as database]
            [planwise.tasks.build-icons :as build-icons]
            [ragtime.core :as ragtime]
            [ragtime.jdbc :as rag-jdbc]
            [duct.migrator.ragtime :as dmr]
            [planwise.boundary.facilities :as facilities]
            [buddy.core.nonce :as nonce]
            [clojure.java.shell :as shell]
            [planwise.virgil])
  (:import org.apache.commons.codec.binary.Hex))

(defn db
  []
  (let [[_ database] (ig/find-derived-1 system :duct.database/sql)]
    database))

(defn rebuild-cljs
  []
  (let [figwheel (:duct.server/figwheel system)]
    (figwheel/rebuild-cljs figwheel)))

(defn clean-cljs
  []
  (let [builds (get-in config [:duct.server/figwheel :builds])]
    (dorun (for [{:keys [build-options]} builds]
             (do
               (println "Cleaning CLJS in" (:output-dir build-options))
               (fig-utils/clean-cljs-build* build-options))))))

(defn run-tests
  [tests]
  (eftest/run-tests tests {:multithread? false}))

(defn test
  ([]
   (run-tests (eftest/find-tests "test")))
  ([pattern]
   (let [pattern (re-pattern pattern)
         filterer (fn [var-name]
                    (->> (str var-name)
                         (re-find pattern)))
         tests (->> (eftest/find-tests "test")
                    (filter filterer))]
     (run-tests tests))))

(defn load-sql
  []
  (database/load-sql-functions (db)))

(defn rollback-1
  []
  (let [[_ index] (ig/find-derived-1 system :duct.migrator/ragtime)
        [_ logger] (ig/find-derived-1 system :duct/logger)
        store (rag-jdbc/sql-database (:spec (db)))
        options {:reporter (dmr/logger-reporter logger)}]
    (ragtime/rollback-last store index 1 options)))

(defn build-icons
  []
  (build-icons/process-svgs))

(defn preprocess-facilities
  ([]
   (preprocess-facilities :unprocessed))
  ([filter]
   (let [service (:planwise.component/facilities system)]
     (case filter
       :unprocessed nil
       :all         (facilities/clear-facilities-processed-status! service)
       (throw (IllegalArgumentException. "unknown filter for preprocess-facilities")))
     (facilities/preprocess-isochrones service))))

(defn gen-base-secret
  []
  (-> (nonce/random-bytes 32)
      Hex/encodeHex
      String.))

(defn npm-install
  []
  (println "Running npm install...")
  (let [{:keys [exit out err]} (shell/sh "npm" "install")]
    (cond (zero? exit) (println out)
          :else (do
                  (println "Error running npm install - exit code" exit)
                  (print err)))))

(defn go
  []
  (npm-install)
  (igr/prep)
  (igr/init))

(defn compile-java
  []
  (planwise.virgil/recompile-all-java))

(defn reset
  []
  (planwise.virgil/refresh)
  (igr/reset))
