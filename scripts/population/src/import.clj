(ns import
  (:gen-class)
  (:require [clojure.java.shell  :as shell]
            [clojure.java.jdbc   :as jdbc]))

(def pg-db {:dbtype "postgresql"
            :dbname "planwise"
            :host "db"
            :port "5432"
            :user "planwise"
            :password "planwise"})

(defn sql-insert!
  [table values]
  (jdbc/insert! pg-db table values))

(defn run-script
  [script-with-options]
  (let [sh-result (apply shell/sh script-with-options)]
    (println (:out sh-result))
    (comment (println (:err sh-result)))))

(defn in?
  [elem coll]
  (some (fn [el](= el elem)) coll))

;
(defn build-cpp
  []
  (println (str "*****************************************************************"))
  (println (str "* Building cpp "))
  (println (str "***"))
  (run-script ["./build-cpp"]))

(defn add-population-source
  [name, filename]
  (sql-insert! :population_sources {:name name :tif_file filename}))

(defn add-country-regions
  [country]
  (println (str "*****************************************************************"))
  (println (str "* Running script to add regions from: " country))
  (println (str "***"))
  (run-script ["./load-regions" country])
  (comment
    (println (:err (shell/sh "sh" "-c" (str "cd /app;" "./scripts/population/load-regions " country) )))))

(defn calculate-country-population
  [country tif-filename-id]
  (println (str "*****************************************************************"))
  (println (str "* Running script to calculate population for: " country " with tif file id: " tif-filename-id))
  (println (str "***"))
  (run-script ["./regions-population" country (str tif-filename-id)]))

(defn raster-all-regions
  [country tif-filename-id]
  (println (str "*****************************************************************"))
  (println (str "* Running script to raster all regions from: " country))
  (println (str "***"))
  (run-script ["./raster-regions" country (str tif-filename-id)]))
;
(defn print-source
  [source]
  (println (:id source))
  (println (:name source))
  (println (:tif_file source)))

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

  (let [result (add-population-source name filename)]
    (doseq [ret result]
      (comment (print-source r))
      (add-country-regions country) ;load-regions
      (calculate-country-population country (:id ret)) ;regions-population
      (raster-all-regions country (:id ret)) ;raster-regions
    ))

  (println (str "All done!"))

  (System/exit 0)) ; https://stackoverflow.com/questions/1134770/how-to-end-force-a-close-to-a-program-in-clojure?rq=1
