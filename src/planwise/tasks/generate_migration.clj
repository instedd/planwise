(ns planwise.tasks.generate-migration
  (:gen-class)
  (:require [clojure.java.io :as io]))

(defn -main
  [name]
  (let [timestamp (.format (java.text.SimpleDateFormat. "yyyyMMddHHmmss") (new java.util.Date))
        path (str "./resources/migrations/")
        filename (str timestamp "-" name ".sql")]
    (with-open [wrtr (io/writer (str path filename))]
      (println timestamp)
      (.write wrtr (str "
-- CREATE TABLE IF NOT EXISTS foo (
--   id BIGSERIAL PRIMARY KEY,
--   bar_id BIGINT NOT NULL REFERENCES bar(id),
--   name VARCHAR(255) NOT NULL
-- );")))))
