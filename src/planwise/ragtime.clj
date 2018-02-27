(ns planwise.ragtime
  (:require [integrant.core :as ig]
            [ragtime.jdbc :as rag-jdbc]
            [ragtime.core :as ragtime]
            [clojure.string :as str]))

(defn- update-migration-id [{:keys [id] :as migration}]
  (assoc migration :id (str/replace id #".*/" "")))

(defmethod ig/init-key :planwise/ragtime
  [_ [path]]
  (->> (rag-jdbc/load-resources path)
       (map update-migration-id)
       (map rag-jdbc/sql-migration)))
