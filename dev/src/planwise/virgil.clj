;; Code to integrate virgil with the reloaded workflow
;; Taken from https://gist.github.com/aiba/a6b22b3acfa55404daebb6666ba20261
(ns planwise.virgil
  (:require [clojure.set :as set]
            [clojure.tools.namespace.dir :as ctn-dir]
            [clojure.tools.namespace.file :as ctn-file]
            [clojure.tools.namespace.find :as ctn-find]
            [clojure.tools.namespace.parse :as ctn-parse]
            [clojure.tools.namespace.repl :as ctn-repl]
            [virgil.compile :refer [compile-all-java]])
  (:import java.io.File
           java.util.Arrays))

;; TODO: only recompile java files that have changed since last compile? (how does
;; this affect java classes that depend on each other? do dependents need to be
;; recompiled also?)

(defonce ^:private java-source-dirs (atom ["src-java"]))
(defonce ^:private last-javac-result (atom []))

(defn set-java-source-dirs! [dirs]
  (reset! java-source-dirs dirs))

;; Takes results of two (compile-all-java) calls and returns set of modified
;; classes, as symbols.
(defn- changed-classes [r1 r2]
  (let [m1 (into {} r1)
        m2 (into {} r2)]
    (set
     (for [k (set/union (set (keys m1))
                        (set (keys m2)))
           :let [^bytes b1 (m1 k)
                 ^bytes b2 (m2 k)]
           :when (not (and b1 b2 (Arrays/equals b1 b2)))]
       (symbol k)))))

;; returns set of classes that have changed since last compile
(defn recompile-all-java []
  (let [r @last-javac-result
        r' (compile-all-java @java-source-dirs)]
    (reset! last-javac-result r')
    (changed-classes r r')))

;; steal private functions
(def ^:private deps-from-libspec #'clojure.tools.namespace.parse/deps-from-libspec)
(def ^:private find-files #'clojure.tools.namespace.dir/find-files)

(defn- all-clj-files []
  (find-files ctn-repl/refresh-dirs
              ctn-find/clj))

(defn- ns-form->imports [ns-form]
  (->> ns-form
       ;; Get all the :imports
       (mapcat (fn [x]
                 (when (and (sequential? x)
                            (= (first x) :import))
                   (rest x))))
       ;; Parse fully qualified class names
       (mapcat #(deps-from-libspec nil %))
       (set)))

(defn- all-java-deps []
  (->> (all-clj-files)
       (pmap (fn [f]
               (let [nsf (ctn-file/read-file-ns-decl f)]
                 (for [klass (ns-form->imports nsf)]
                   [klass f]))))
       (apply concat)
       (reduce (fn [m [klass f]]
                 (update m klass set/union #{f}))
               {})))

;; Given a set of classes (as symbols), return set of clojure files that depend on
;; any of the given classes.
(defn- ns-dependants [klasses]
  (set (mapcat (all-java-deps) klasses)))

;; Main function to be used alongside of clojure.tools.namespace.repl/refresh.
;; Detects which clojure files depend on changed java classes and touches those
;; files, which will cause tools.namespace.repl/refresh to recompile them.
(defn refresh []
  (println "Recompiling java...")
  (let [changed (recompile-all-java)]
    (when (seq changed)
      (println "  Updated" (count changed) "java classes.")
      (let [dirty (ns-dependants changed)
            now (System/currentTimeMillis)]
        (println "  Dirtying" (count dirty) "clj files")
        (doseq [^File f dirty]
          (.setLastModified f now))))))
