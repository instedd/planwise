(ns dev.tasks
  (:refer-clojure :exclude [test])
  (:require [duct.generate :as gen]
            [eftest.runner :as eftest]
            [dev.figwheel :as figwheel]
            [dev.sass :as sass]
            [reloaded.repl :refer [system]]
            [planwise.tasks.db :refer [load-sql-functions]]))

(defn setup []
  (gen/locals))

(defn test
  ([]
   (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))
  ([pattern]
   (let [pattern (re-pattern pattern)
         filterer (fn [var-name]
                    (->> (str var-name)
                         (re-find pattern)))
         tests (->> (eftest/find-tests "test")
                    (filter filterer))]
     (eftest/run-tests tests {:multithread? false}))))

(defn cljs-repl []
  (figwheel/cljs-repl (:figwheel system)))

(defn rebuild-css []
  (sass/rebuild (:sass system))
  (figwheel/refresh-css (:figwheel system)))

(defn load-sql []
  (load-sql-functions system))
