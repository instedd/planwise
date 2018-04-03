(ns planwise.tasks.generate-migration
  (:gen-class)
  (:require [clojure.java.io :as io]))

(defn -main
  [name]
  (let [timestamp (.format (java.text.SimpleDateFormat. "yyyyMMddHHmmss") (new java.util.Date))]
    (with-open [wrtr (io/writer (str "./resources/migrations/" timestamp "_" name ".sql"))]
      (println timestamp)
      (.write wrtr (str "Line to be written")))))
