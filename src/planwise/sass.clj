(ns planwise.sass
  (:import [io.bit3.jsass Options OutputStyle]
           [io.bit3.jsass.context FileContext])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [duct.logger :as log]
            [integrant.core :as ig]
            [medley.core :as m]))

(def ^:private compiler (io.bit3.jsass.Compiler.))

(def ^:private re-ext
  (re-pattern (str "\\.([^\\" java.io.File/separator "]*?)$")))

(defn- file-ext [f]
  (second (re-find re-ext (str f))))

(defn- find-files [dir exts]
  (filter (comp (set exts) file-ext) (file-seq (io/file dir))))

(defn- relative-path [dir file]
  (str (.relativize (.toPath (io/file dir)) (.toPath (io/file file)))))

(defn- css-output-file [input-file source-path output-path]
  (-> (relative-path source-path input-file)
      (str/replace re-ext ".css")
      (as-> f (io/file output-path f))))

(def ^:private re-partial
  (re-pattern (str "_[^\\" java.io.File/separator "]*?$")))

(defn- partial-file? [f]
  (re-find re-partial (str f)))

(defn- file-mapping-1 [source-path output-path]
  (->> (find-files source-path ["scss" "sass"])
       (remove partial-file?)
       (map (juxt identity #(css-output-file % source-path output-path)))
       (into {})))

(defn- file-mapping [{:keys [source-paths output-path]}]
  (into {} (map #(file-mapping-1 % output-path)) source-paths))

(defn- timestamp-map [files]
  (into {} (map (juxt identity #(.lastModified %)) files)))

(def ^:private output-styles
  {:compact    OutputStyle/COMPACT
   :compressed OutputStyle/COMPRESSED
   :expanded   OutputStyle/EXPANDED
   :nested     OutputStyle/NESTED})

(defn- source-map-uri [file]
  (.toURI (io/file (str file ".map"))))

(defn- make-options [in out opts]
  (doto (Options.)
    (.setOutputStyle (output-styles (:output-style opts :nested)))
    (.setIndent (:indent opts "  "))
    (.setIncludePaths (java.util.ArrayList. (map io/file (:include-paths opts))))
    (.setSourceMapFile (if (:source-map? opts) (source-map-uri out)))))

(defn- compile-sass [in out {:keys [logger] :as opts}]
  (log/log logger :info ::compiling {:in (str in) :out (str out)})
  (let [context (FileContext. (.toURI in) (.toURI out) (make-options in out opts))
        result  (.compile compiler context)]
    (.mkdirs (.getParentFile out))
    (spit out (.getCss result))
    (when-let [source-map (.getSourceMap result)]
      (spit (source-map-uri out) source-map))))

(defmethod ig/init-key :planwise/sass [_ opts]
  (let [in->out (file-mapping opts)]
    (doseq [[in out] in->out]
      (compile-sass in out opts))
    (mapv (comp str val) in->out)))

(derive :planwise/sass :duct/compiler)
