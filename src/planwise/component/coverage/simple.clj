(ns planwise.component.coverage.simple
  (:require [hugsql.core :as hugsql]
            [planwise.util.pg :as pg]))

(hugsql/def-db-fns "planwise/sql/coverage/simple.sql")

(defn compute-coverage
  [db-spec point distance]
  (compute-simple-buffer-coverage db-spec {:point point :distance distance}))


;; REPL testing

(comment
  ;; Kilifi location
  (compute-coverage (:spec (planwise.repl/db)) (pg/make-point -3.0361 40.1333) 20000)

  ;; Nairobi location
  (compute-coverage (:spec (planwise.repl/db)) (pg/make-point -1.2741 36.7931) 10000))
