(ns planwise.component.providers-set
  (:require [planwise.boundary.providers-set :as boundary]
            [integrant.core :as ig]
            [clojure.string :refer [includes?]]
            [taoensso.timbre :as timbre]
            [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [planwise.util.files :as files]
            [planwise.util.collections :refer [csv-data->maps]]
            [clojure.string :as str]
            [clojure.set :as set]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/providers_set.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

(defn- import-provider
  [store provider-set-id version csv-provider-data]
  (try
    (let [data {:source-id (Integer. (:id csv-provider-data))
                :type (:type csv-provider-data)
                :version version
                :provider-set-id provider-set-id
                :name (:name csv-provider-data)
                :lat  (Double. (:lat csv-provider-data))
                :lon  (Double. (:lon csv-provider-data))
                :capacity (Integer. (:capacity csv-provider-data))
                :tags (:tags csv-provider-data)}]
      (db-create-provider! (get-db store) data))
    (catch Exception e
      (warn "Failed to import provider " csv-provider-data)
      (throw e))))

(defn csv-to-providers
  "Generates providers from a provider-set-id and a csv file"
  [store provider-set-id csv-file]
  (let [reader     (io/reader csv-file)
        providers-data (csv-data->maps (csv/read-csv reader))
        version    (:last-version (db-create-provider-set-version! (get-db store) {:id provider-set-id}))]
    (doall (map #(import-provider store provider-set-id version %) providers-data))))

(defn providers-by-version
  "Returns providers associated to a provider-set-id and version"
  [store provider-set-id version]
  (db-find-providers (get-db store) {:provider-set-id provider-set-id
                                     :version version}))

;; ----------------------------------------------------------------------
;; Service definition

(defn create-provider-set
  [store name owner-id]
  (db-create-provider-set! (get-db store) {:name name
                                           :owner-id owner-id}))

(defn list-providers-set
  [store owner-id]
  (db-list-providers-set (get-db store) {:owner-id owner-id}))

(defn get-provider-set
  [store provider-set-id]
  (db-find-provider-set (get-db store) {:id provider-set-id}))

(defn get-provider
  [store provider-id]
  (db-find-provider (get-db store) {:id provider-id}))

(defn create-and-import-providers
  [store {:keys [name owner-id]} csv-file]
  (jdbc/with-db-transaction [tx (get-db store)]
    (let [tx-store (assoc-in store [:db :spec] tx)
          create-result (create-provider-set tx-store name owner-id)
          provider-set-id (:id create-result)]
      (csv-to-providers tx-store provider-set-id csv-file)
      (get-provider-set tx-store provider-set-id))))


;;;
;;; Pre-processing job implementation
;;;


(defn- provider-matches-tags?
  [provider tags]
  (let [filter-tags (set tags)
        provider-tags (set (str/split (:tags provider) #" "))]
    (or (empty? tags)
        (not (empty? (set/intersection filter-tags provider-tags))))))

(defn get-providers-in-region
  [store provider-set-id version filter-options]
  (let [db-spec             (get-db store)
        config              {:provider-set-id provider-set-id
                             :version         version
                             :region-id       (:region-id filter-options)}
        all-providers       (db-find-providers-in-region db-spec config)
        providers-partition (group-by
                             #(provider-matches-tags? % (:tags filter-options))
                             all-providers)]
    {:providers          (or (get providers-partition true) [])
     :disabled-providers (or (get providers-partition false) [])}))

(defn count-providers-filter-by-tags
  ([store provider-set-id region-id tags]
   (count-providers-filter-by-tags store provider-set-id region-id tags nil))
  ([store provider-set-id region-id tags version]
   (let [db-spec  (get-db store)
         tags     (str/join " & " tags)
         count-fn (fn [tags version]
                    (let [{:keys [last-version]} (get-provider-set store provider-set-id)]
                      (:count (db-count-providers-with-tags db-spec {:provider-set-id provider-set-id
                                                                     :version         (or version last-version)
                                                                     :region-id       region-id
                                                                     :tags            tags}))))
         total     (count-fn "" version)
         response {:total total :filtered total}]
     (if (str/blank? tags) response (assoc response :filtered (count-fn tags version))))))

(defn delete-provider-set
  [store provider-set-id]
  (try
    (jdbc/with-db-transaction [tx (get-db store)]
      (let [tx-store        (assoc-in store [:db :spec] tx)
            params          {:provider-set-id provider-set-id}]
        (db-delete-providers! tx params)
        (db-delete-provider-set! tx params)))
    (catch Exception e
      (throw (ex-info "Provider set can not be deleted"
                      {:provider-set-id provider-set-id}
                      e)))))


(defrecord ProvidersStore [db]
  boundary/ProvidersSet
  (list-providers-set [store owner-id]
    (list-providers-set store owner-id))
  (get-provider-set [store provider-set-id]
    (get-provider-set store provider-set-id))
  (get-provider [store provider-id]
    (get-provider store provider-id))
  (create-and-import-providers [store options csv-file]
    (create-and-import-providers store options csv-file))
  (get-providers-in-region [store provider-set-id version filter-options]
    (get-providers-in-region store provider-set-id version filter-options))
  (count-providers-filter-by-tags [store provider-set-id region-id tags]
    (count-providers-filter-by-tags store provider-set-id region-id tags))
  (count-providers-filter-by-tags [store provider-set-id region-id tags version]
    (count-providers-filter-by-tags store provider-set-id region-id tags version))
  (delete-provider-set [store provider-set-id]
    (delete-provider-set store provider-set-id)))

(defmethod ig/init-key :planwise.component/providers-set
  [_ config]
  (map->ProvidersStore config))

