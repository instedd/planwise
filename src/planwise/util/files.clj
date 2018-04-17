(ns planwise.util.files
  (:require [clojure.java.io :as io]))

(defn delete-files-recursively [fname & [silently]]
  (letfn [(delete-f [file]
            (when (.isDirectory file)
              (doseq [child-file (.listFiles file)]
                (delete-f child-file)))
            (clojure.java.io/delete-file file silently))]
    (delete-f (clojure.java.io/file fname))))

(defn create-temp-file
  [parent prefix suffix]
  (let [parent (io/as-file parent)]
    (io/make-parents parent)
    (.mkdir parent)
    (str (java.io.File/createTempFile prefix suffix parent))))
