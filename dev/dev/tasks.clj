(ns dev.tasks
  (:refer-clojure :exclude [test])
  (:require [duct.generate :as gen]
            [eftest.runner :as eftest]
            [duct.component.figwheel :as figwheel]
            [dev.sass :as sass]
            [reloaded.repl :refer [system]]))

(defn setup []
  (gen/locals))

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(defn cljs-repl []
  (figwheel/cljs-repl (:figwheel system)))

(defn rebuild-css []
  (sass/rebuild (:sass system))
  (figwheel/refresh-css (:figwheel system)))
