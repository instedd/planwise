(ns planwise.repl
  (:refer-clojure :exclude [test])
  (:require [integrant.core :as ig]
            [integrant.repl :as igr]
            [integrant.repl.state :refer [config system]]
            [eftest.runner :as eftest]
            [planwise.database :as database]
            [planwise.tasks.build-icons :as build-icons]
            [ragtime.core :as ragtime]
            [ragtime.jdbc :as rag-jdbc]
            [duct.migrator.ragtime :as dmr]
            [buddy.core.nonce :as nonce]
            [planwise.virgil])
  (:import org.apache.commons.codec.binary.Hex))

(defn db
  []
  (let [[_ database] (ig/find-derived-1 system :duct.database/sql)]
    database))

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

(defn gen-base-secret
  []
  (-> (nonce/random-bytes 32)
      Hex/encodeHex
      String.))

(defn go
  []
  (igr/prep)
  (igr/init))

(defn compile-java
  []
  (planwise.virgil/recompile-all-java))

(defn reset
  []
  (planwise.virgil/refresh)
  (igr/reset))
