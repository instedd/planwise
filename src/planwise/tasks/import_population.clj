(ns planwise.tasks.import-population
  (:gen-class)
  (:require [clojure.java.shell  :as shell]
            [clojure.java.jdbc   :as jdbc]))

(def pg-db {:dbtype "postgresql"
            :dbname (System/getenv "POSTGRES_DB")
            :host (System/getenv "POSTGRES_HOST")
            :port (System/getenv "POSTGRES_PORT")
            :user (System/getenv "POSTGRES_USER")
            :password (System/getenv "POSTGRES_PASSWORD")})

(defn sql-insert!
  [table values]
  (jdbc/insert! pg-db table values))

(defn run-script
  [script-with-options]
  (comment (println script-with-options))
  (apply shell/sh script-with-options))


(defn add-script-path
  [script-name]
  (str "./scripts/population/" script-name))

(defn in?
  [elem coll]
  (some (fn [el] (= el elem)) coll))

;
(defn build-cpp
  []
  (println (str "*****************************************************************"))
  (println (str "* Building cpp "))
  (println (str "***"))
  (run-script [(add-script-path "build-cpp")]))

(defn add-population-source
  [name filename]
  (sql-insert! :population_sources {:name name :tif_file filename}))

(defn add-country-regions
  [country]
  (run-script [(add-script-path "load-regions") country]))

(defn calculate-country-population
  [country tif-filename-id]
  (run-script [(add-script-path "regions-population") country (str tif-filename-id)]))

(defn raster-all-regions
  [country tif-filename-id]
  (run-script [(add-script-path "raster-regions") country (str tif-filename-id)]))
;
(defn print-source
  [source]
  (println (:id source))
  (println (:name source))
  (println (:tif_file source)))

(defn print-add-country-regions-header
  [verbose country]
  (when verbose (println (str "*****************************************************************")))
  (println (str "* Running script to add regions from: " country))
  (when verbose (println (str "***"))))

(defn print-calculate-country-population-header
  [verbose country tif-filename-id]
  (when verbose (println (str "*****************************************************************")))
  (println (str "* Running script to calculate population for: " country " with tif file id: " tif-filename-id))
  (when verbose (println (str "***"))))

(defn print-raster-all-regions-header
  [verbose country]
  (when verbose (println (str "*****************************************************************")))
  (println (str "* Running script to raster all regions from: " country))
  (when verbose (println (str "***"))))

(defn print-script-result
  [verbose script-result]
  (when verbose
    (println (:out script-result))
    (println (:err script-result))))

;
(defn -main
  [name filename country & options]

  (println (str "*****************************************************************"))
  (println (str "* Parameters: "))
  (println (str "***"))
  (println (str "   -> Name: " name))
  (println (str "   -> Filename: " filename))
  (println (str "   -> Country: " country))
  (println (str ""))

  (when (in? "--build-cpp" options)
    (build-cpp)) ;build programs in app/cpp/

  (let [verbose (in? "--verbose" options)
        sql-result (add-population-source name filename)
        print-result #(print-script-result verbose %)
        print-header-add-country-regions #(print-add-country-regions-header verbose %)
        print-header-calculate-country-population #(print-calculate-country-population-header verbose %1 %2)
        print-header-raster-all-regions #(print-raster-all-regions-header verbose %)]

    (doseq [ret sql-result]
      (comment (print-source ret))

      (print-header-add-country-regions country)
      (-> (add-country-regions country) ;load-regions
          (print-result))

      (print-header-calculate-country-population country (:id ret))
      (-> (calculate-country-population country (:id ret)) ;regions-population
          (print-result))

      (print-header-raster-all-regions country)
      (-> (raster-all-regions country (:id ret)) ;raster-regions
          (print-result))))


  (println (str "All done!"))

  (System/exit 0))
