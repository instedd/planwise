(ns import
  (:gen-class)
  (:require [ clojure.java.shell  :as shell ]
            [ clojure.java.jdbc   :as jdbc  ]))

(def pg-db  { :dbtype "postgresql"
              :dbname "planwise"
              :host "db"
              :port "5432"
              :user "planwise"
              :password "planwise" })

(def sql-query
  #(jdbc/query pg-db %))

(def sql-insert! ; db-spec :table {:col1 42 :col2 "123"}
  #(jdbc/insert! pg-db %1 %2))

(def run-script
  #(let [sh-result (apply shell/sh %1)]
      (println (:out sh-result))
      (println (:err sh-result))))

;
(defn add-population-source
  [name, filename]
  (sql-insert! :population_sources {:name name :tif_file filename}))

(defn add-country-regions
  [country]
  (println (str "Running script to add regions from: " country))
  (run-script ["./load-regions" country]))

(defn calculate-country-population
  [country tif-filename]
  (println (str "Running script to calculate population for: " country " with tif file id: " tif-filename))
  (run-script ["./regions-population" country (str tif-filename)]))

;
(defn print-source
  [source]
  (println (:id source))
  (println (:name source))
  (println (:tif_file source)))

;
(defn -main
  [name filename country]

  (println (str "Name: " name))
  (println (str "Filename: " filename))
  (println (str "Country: " country))

  (let [ result (add-population-source name filename) ]
    (doseq [r result]
      (print-source r)
      (add-country-regions country) ;load-regions
      (calculate-country-population country (:id r)) ;regions-population
      ; TODO: (rasterize-all-regions)
    ))

  (System/exit 0)) ; https://stackoverflow.com/questions/1134770/how-to-end-force-a-close-to-a-program-in-clojure?rq=1
