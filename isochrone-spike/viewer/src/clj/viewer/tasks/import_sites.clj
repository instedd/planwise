(ns viewer.tasks.import-sites
  (:require [viewer.facilities :as facilities]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import (java.util.zip GZIPInputStream))
  (:gen-class))

(defn file-reader [path]
  (if (.endsWith path ".gz")
    (-> path
        (io/input-stream)
        (GZIPInputStream.)
        (io/reader))
    (-> path
        (io/reader))))

(defn load-sites [path]
  (println (str "Reading facility sites from " path))
  (with-open [data (file-reader path)]
    (-> data
        (json/read :key-fn keyword)
        (:sites))))

(defn import-sites-from-file [file]
  (let [sites (load-sites file)
        facilities (facilities/sites->facilities sites)]
    (println (str "Read " (count sites) " sites"))
    (println (str "Will import " (count facilities) " into the database"))
    (facilities/init-facilities)
    (let [insert-count (facilities/insert-facilities! facilities)]
      (println (str "Successfully imported " insert-count " facilities")))))

(defn -main [& args]
  (if-let [sites-file (first args)]
    (import-sites-from-file sites-file)
    (println "Please specify the path to the sites JSON file exported from Resourcemap.")))
