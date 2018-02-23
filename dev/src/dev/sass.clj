(ns dev.sass
  (:require [com.stuartsierra.component :as c]
            [clojure.java.io :refer [make-parents]]
            [clojure.java.shell :as shell]))

(defn- quoted [s]
  (str \" s \"))

(defn build-sass [input output options]
  (let [opts (or options [])
        input-file (or input "resources/sass/site.scss")
        output-file (or output "target/sass/planwise/public/css/site.css")]
    (println "Compiling" (quoted output-file) "from" (str \" input-file \"))
    (make-parents output-file)
    (let [result (apply shell/sh (concat ["sassc"] opts [input-file output-file]))]
      (when (not= 0 (:exit result))
        (println "Error compiling SASS: " (:err result))))))

(defrecord SassCompiler [input output options]
  c/Lifecycle
  (start [component]
    (build-sass input output options)
    (assoc component :loaded true))
  (stop [component]
    (assoc component :loaded false)))

(defn sass-compiler [config]
  (map->SassCompiler config))

(defn rebuild [component]
  (let [input (:input component)
        output (:output component)
        options (:options component)]
    (build-sass input output options)))
