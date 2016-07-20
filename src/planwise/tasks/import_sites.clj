(ns planwise.tasks.import-sites
  (:require [planwise.component.facilities :as facilities]
            [planwise.config :as config]
            [com.stuartsierra.component :as component]
            [duct.component.hikaricp :refer [hikaricp]]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.set :refer [rename-keys]]
            [meta-merge.core :refer [meta-merge]])
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

(defn sites-with-location [sites]
  (filter #(and (:lat %) (:long %)) sites))

(defn facility-type [site]
  (let [f_type (get-in site [:properties :f_type])]
    (case f_type
      1 1   ;; "hospital"
      2 2   ;; "general hospital"
      3 3   ;; "health center"
      4 ))) ;; "dispensary")))

(defn site->facility [site]
  (-> site
      (select-keys [:id :name :lat :long])
      (rename-keys {:long :lon})
      (assoc :type_id (facility-type site))))

(defn sites->facilities [sites]
  (->> sites
       (sites-with-location)
       (map site->facility)))

(defn import-sites-from-file [{service :facilities} file]
  (let [sites (load-sites file)
        facilities (sites->facilities sites)]
    (println (str "Read " (count sites) " sites"))
    (println (str "Will import " (count facilities) " into the database"))
    (facilities/destroy-facilities! service)
    (let [insert-count (facilities/insert-facilities! service facilities)]
      (println (str "Successfully imported " insert-count " facilities")))))

(def config
  (meta-merge config/defaults
              config/environ))

(defn new-system [config]
  (-> (component/system-map
       :db (hikaricp (:db config))
       :facilities (facilities/facilities-service))
      (component/system-using
       {:facilities [:db]})))

(defn -main [& args]
  (if-let [sites-file (first args)]
    (let [system (new-system config)
          system (component/start system)]
      (import-sites-from-file system sites-file)
      (component/stop system))
    (println "Please specify the path to the sites JSON file exported from Resourcemap.")))
