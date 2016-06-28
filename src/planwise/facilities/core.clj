(ns planwise.facilities.core
  (:require [clojure.set :refer [rename-keys]]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]))

(hugsql/def-db-fns "planwise/facilities/facilities.sql")

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

(defn insert-facilities! [db facilities]
  (jdbc/with-db-transaction [tx db]
    (doseq [facility facilities]
      (insert-facility! tx facility)))
  (count facilities))

(defn destroy-facilities! [db]
  (delete-facilities! db))

;; (insert-facilities! db facilities)
;; (delete-facilities! db)

;; (select-facilities db)
(defn get-facilities [db]
  (select-facilities db))

;; (def db (:spec (:db reloaded.repl/system)))
;; (facilities-with-isochrones db {:threshold 90})
(defn get-with-isochrones
  ([db threshold]
   (get-with-isochrones db threshold "alpha-shape" 0.0))
  ([db threshold method simplify]
   (facilities-with-isochrones db {:threshold threshold,
                                   :method method,
                                   :simplify simplify})))

(defn get-isochrone-facilities [db threshold]
  (isochrone-for-facilities db {:threshold threshold}))
