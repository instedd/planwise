(ns planwise.component.coverage.pgrouting
  (:require [hugsql.core :as hugsql]
            [planwise.util.pg :as pg]
            [clojure.spec.alpha :as s]
            [clojure.java.jdbc.spec :as sjdbc]))

(hugsql/def-db-fns "planwise/sql/coverage/pgrouting.sql")

(s/def ::threshold pos-int?)

(defn compute-coverage
  [db-spec point threshold]
  {:pre [(s/valid? ::sjdbc/db-spec db-spec)
         (s/valid? ::pg/point point)
         (s/valid? ::threshold threshold)]}
  (compute-pgr-alpha-coverage db-spec {:point point :threshold threshold}))

(defn get-closest-way-node
  [db-spec point]
  {:pre [(s/valid? ::sjdbc/db-spec db-spec)
         (s/valid? ::pg/point point)]}
  (get-closest-node  db-spec {:point point}))

;; REPL testing

(comment
  ;; Kilifi location
  (compute-coverage (:spec (planwise.repl/db)) (pg/make-point -3.0361 40.1333) 180)

  ;; Nairobi location
  (compute-coverage (:spec (planwise.repl/db)) (pg/make-point -1.2741 36.7931) 180))

