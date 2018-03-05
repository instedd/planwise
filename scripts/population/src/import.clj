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

(defn add-population-source
  [name, filename]
  (sql-insert! :population_sources {:name name :tif_file filename}))

;
;
(defn print-source
  [source]
  (println (:id source))
  (println (:name source))
  (println (:tif_file source)))


(defn -main
  [name filename country]

  (println (str "Name: " name))
  (println (str "Filename: " filename))
  (println (str "Country: " country))

  ; (println (:out (shell/sh "ls" "-al")))

  (let [ sources (sql-query ["select * from population_sources where id = ?" 1]) ]
    (map print-source sources)
  )

  (add-population-source name filename)

  (System/exit 0)) ; https://stackoverflow.com/questions/1134770/how-to-end-force-a-close-to-a-program-in-clojure?rq=1
