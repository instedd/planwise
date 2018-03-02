(ns planwise.repl
  (:refer-clojure :exclude [test])
  (:require [integrant.core :as ig]
            [integrant.repl.state :refer [config system]]
            [duct.server.figwheel :as figwheel]
            [eftest.runner :as eftest]
            [planwise.database :as database]
            [ragtime.core :as ragtime]
            [ragtime.jdbc :as rag-jdbc]
            [duct.migrator.ragtime :as dmr]))

(defn db
  []
  (let [[_ database] (ig/find-derived-1 system :duct.database/sql)]
    database))

(defn rebuild-cljs
  []
  (let [figwheel (:duct.server/figwheel system)]
    (figwheel/rebuild-cljs figwheel)))

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
