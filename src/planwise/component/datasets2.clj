(ns planwise.component.datasets2
  (:require [planwise.boundary.datasets2 :as boundary]
            [planwise.boundary.coverage :as coverage]
            [planwise.component.coverage :refer [supported-algorithms]]
            [planwise.component.jobrunner :as jr]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/datasets2.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

(defn- csv-data->maps
  [csv-data]
  (map zipmap
       (->> (first csv-data)
            (map keyword)
            repeat)
       (rest csv-data)))

(defn- import-site
  [store dataset-id version csv-site-data]
  (let [data {:source-id (Integer. (:id csv-site-data))
              :type (:type csv-site-data)
              :version version
              :dataset-id dataset-id
              :name (:name csv-site-data)
              :lat  (Double. (:lat csv-site-data))
              :lon  (Double. (:lon csv-site-data))
              :capacity (Integer. (:capacity csv-site-data))
              :tags (:tags csv-site-data)}]
    (db-create-site! (get-db store) data)))

(defn csv-to-sites
  "Generates sites from a dataset-id and a csv file"
  [store dataset-id csv-file]
  (let [reader     (io/reader csv-file)
        sites-data (csv-data->maps (csv/read-csv reader))
        version    (:last-version (db-create-dataset-version! (get-db store) {:id dataset-id}))]
    (doall (map #(import-site store dataset-id version %) sites-data))))

(defn sites-by-version
  "Returns sites associated to a dataset-id and version"
  [store dataset-id version]
  (db-find-sites (get-db store) {:dataset-id dataset-id
                                 :version version}))

;; ----------------------------------------------------------------------
;; Service definition

(defn create-dataset
  [store name owner-id coverage-algorithm]
  (db-create-dataset! (get-db store) {:name name
                                      :owner-id owner-id
                                      :coverage-algorithm (some-> coverage-algorithm clojure.core/name)}))

(defn list-datasets
  [store owner-id]
  (db-list-datasets (get-db store) {:owner-id owner-id}))

(defn get-dataset
  [store dataset-id]
  (db-find-dataset (get-db store) {:id dataset-id}))

(defn create-and-import-sites
  [store {:keys [name owner-id coverage-algorithm]} csv-file]
  (jdbc/with-db-transaction [tx (get-db store)]
    (let [tx-store (assoc-in store [:db :spec] tx)
          create-result (create-dataset tx-store name owner-id coverage-algorithm)
          dataset-id (:id create-result)]
      (csv-to-sites tx-store dataset-id csv-file)
      (get-dataset tx-store dataset-id))))


;;;
;;; Pre-processing job implementation
;;;

(defn compute-site-coverage!
  [store site {:keys [algorithm options raster-dir]}]
  (try
    (let [db-spec         (get-db store)
          coverage        (:coverage store)
          coords          (select-keys site [:lat :lon])
          site-id         (:id site)
          raster-basename (str/join "_" (flatten [site-id (name algorithm) (vals options)]))
          raster-filename (str raster-basename ".tif")
          raster-path     (str (io/file raster-dir raster-filename))
          criteria        (merge {:algorithm algorithm
                                  :raster    raster-path}
                                 options)
          polygon         (coverage/compute-coverage coverage coords criteria)
          site-coverage   {:site-id   site-id
                           :algorithm (name algorithm)
                           :options   (pr-str options)
                           :geom      polygon
                           :raster    raster-basename}
          result          (db-create-site-coverage! db-spec site-coverage)]
      {:ok (:id result)})
    (catch RuntimeException e
      (warn "Error" (.getMessage e) "processing coverage for site" (:id site)
            "using algorithm" algorithm "with options" (pr-str options))
      {:error (.getMessage e)})))

(defn preprocess-site!
  [store site-id {:keys [algorithm options-list raster-dir]}]
  {:pre [(some? algorithm)]}
  (let [db-spec    (get-db store)
        site       (db-fetch-site-by-id db-spec {:id site-id})]
    (if (nil? (:processing-status site))
      (do
        (info "Pre-processing site" site-id)
        ;; TODO: delete old raster as well as the database records
        (db-delete-algorithm-coverages-by-site-id! db-spec {:site-id site-id :algorithm (name algorithm)})
        (let [results (doall (for [options options-list]
                               (compute-site-coverage! store site {:algorithm algorithm
                                                                   :options options
                                                                   :raster-dir raster-dir})))]
          (let [total     (count options-list)
                succeeded (count (filter (comp some? :ok) results))
                result    (condp = succeeded
                            total :ok
                            0 :error
                            :partial)]
            (db-update-site-processing-status! db-spec {:id site-id
                                                        :processing-status (str result)}))))
      (info "Skipping site" site-id
            "since it's already processed with status" (:processing-status site)))))

(defn new-processing-job
  "Returns the initial job state to pre-process the datasets' sites"
  [store dataset-id]
  (let [db-spec      (get-db store)
        coverage     (:coverage store)
        dataset      (db-find-dataset db-spec {:id dataset-id})
        last-version (:last-version dataset)
        algorithm    (keyword (:coverage-algorithm dataset))
        sites        (db-enum-site-ids db-spec {:dataset-id dataset-id :version last-version})
        options-list (coverage/enumerate-algorithm-options coverage algorithm)
        ;; TODO: configure the raster-dir in the component
        raster-dir   (str (io/file "data/coverage" (str dataset-id)))]

    (cond
      (some? algorithm)
      {:store   store
       :options {:algorithm    algorithm
                 :options-list options-list
                 :raster-dir   raster-dir}
       :sites   sites}

      :else
      (do
        (warn "Coverage algorithm not set for dataset" dataset-id)
        nil))))

(defmethod jr/job-next-task ::boundary/preprocess-dataset
  [[_ dataset-id] {:keys [store options sites] :as state}]
  (let [next-site (first sites)
        sites'    (next sites)
        state'    (when sites' (assoc state :sites sites'))]
    (if (some? next-site)
      (let [site-id (:id next-site)]
        {:state state'
         :task-id site-id
         :task-fn (fn [] (preprocess-site! store site-id options))})
      {:state state'})))

(defn preprocess-dataset!
  "Manually trigger the synchronous pre-processing of a dataset"
  [store dataset-id]
  (info "Pre-processing sites for dataset" dataset-id)
  (if-let [{:keys [sites options]} (new-processing-job store dataset-id)]
    (dorun (for [site-id (map :id sites)]
             (preprocess-site! store site-id options)))
    (info "Coverage algorithm not set for dataset" dataset-id)))


(defrecord SitesDatasetsStore [db coverage]
  boundary/Datasets2
  (list-datasets [store owner-id]
    (list-datasets store owner-id))
  (get-dataset [store dataset-id]
    (get-dataset store dataset-id))
  (create-and-import-sites [store options csv-file]
    (create-and-import-sites store options csv-file))
  (new-processing-job [store dataset-id]
    (new-processing-job store dataset-id)))


(defmethod ig/init-key :planwise.component/datasets2
  [_ config]
  (map->SitesDatasetsStore config))

(comment
  ;; REPL testing

  (def store (:planwise.component/datasets2 integrant.repl.state/system))

  (preprocess-site! store 1 {:algorithm :simple-buffer
                             :options-list [{:distance 5} {:distance 10}]
                             :raster-dir "data/coverage/11"})

  (preprocess-site! store 1 {:algorithm :pgrouting-alpha
                             :options-list [{:driving-time 30} {:driving-time 60}]
                             :raster-dir "data/coverage/11"})

  (preprocess-dataset! store 11))
