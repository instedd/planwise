(ns viewer.facilities
  (:require [clojure.set :refer [rename-keys]]
            [clojure.java.jdbc :as jdbc]
            [viewer.db :refer [db]]
            [hugsql.core :as hugsql]))

(hugsql/def-db-fns "sql/facilities.sql")
;; (hugsql/def-sqlvec-fns "sql/facilities.sql")

;; (create-facilities-table db)
;; (create-facilities-spatial-index db)
;; (drop-facilities-table! db)
(defn init-facilities []
  (drop-facilities-table! db)
  (create-facilities-table db)
  (create-facilities-spatial-index db))

(defn sites-with-location [sites]
  (filter #(and (:lat %) (:long %)) sites))

(defn site->facility [site]
  (-> site
      (select-keys [:id :name :lat :long])
      (rename-keys {:long :lon})))

(defn sites->facilities [sites]
  (->> sites
       (sites-with-location)
       (map site->facility)))

;; (def facilities (sites->facilities sites))
;; (first facilities)

;; (insert-facility db (first facilities))

(defn insert-facilities! [facilities]
  (jdbc/with-db-transaction [tx db]
    (doseq [facility facilities]
      (insert-facility! tx facility)))
  (count facilities))

(defn destroy-facilities! []
  (delete-facilities! db))

;; (insert-facilities! facilities)
;; (delete-facilities! db)

;; (select-facilities db)
(defn get-facilities []
  (select-facilities db))
