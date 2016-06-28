(ns planwise.tasks.import-sites
  (:require [planwise.facilities.core :as facilities]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [com.stuartsierra.component :as component]
            [duct.component.hikaricp :refer [hikaricp]]
            [meta-merge.core :refer [meta-merge]]
            [planwise.config :as config])
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

(defn import-sites-from-file [{{db :spec} :db} file]
  (let [sites (load-sites file)
        facilities (facilities/sites->facilities sites)]
    (println (str "Read " (count sites) " sites"))
    (println (str "Will import " (count facilities) " into the database"))
    (facilities/delete-facilities! db)
    (let [insert-count (facilities/insert-facilities! db facilities)]
      (println (str "Successfully imported " insert-count " facilities")))))

(def config
  (meta-merge config/defaults
              config/environ))

(defn new-system [config]
  (-> (component/system-map
       :db (hikaricp (:db config)))))

(defn -main [& args]
  (if-let [sites-file (first args)]
    (let [system (new-system config)
          system (component/start system)]
      (import-sites-from-file system sites-file)
      (component/stop system))
    (println "Please specify the path to the sites JSON file exported from Resourcemap.")))
