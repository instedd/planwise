(ns planwise.projects.core
  (:require [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]))

(hugsql/def-db-fns "planwise/projects/projects.sql")

(defn create-project [db project]
  (let [id (-> (insert-project! db project) (first) (:id))]
    (assoc project :id id)))
