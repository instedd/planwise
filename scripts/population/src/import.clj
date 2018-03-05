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

(defn -main
  [name filename country]

  (println (str "Name: " name))
  (println (str "Filename: " filename))
  (println (str "Country: " country))

  ; (println (:out (shell/sh "ls" "-al")))

  (jdbc/query pg-db
    ["select * from table where foo = ?" "bar"])

  (System/exit 0))
