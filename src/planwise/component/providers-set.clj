(ns planwise.component.providers-set
  (:require [planwise.boundary.providers-set :as boundary]
            [planwise.boundary.coverage :as coverage]
            [planwise.boundary.jobrunner :as jr]
            [integrant.core :as ig]
            [clojure.string :refer [includes?]]
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

(hugsql/def-db-fns "planwise/sql/providers_set.sql")

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
  [store provider-set-id version csv-site-data]
  (let [data {:source-id (Integer. (:id csv-site-data))
              :type (:type csv-site-data)
              :version version
              :provider-set-id provider-set-id
              :name (:name csv-site-data)
              :lat  (Double. (:lat csv-site-data))
              :lon  (Double. (:lon csv-site-data))
              :capacity (Integer. (:capacity csv-site-data))
              :tags (:tags csv-site-data)}]
    (db-create-site! (get-db store) data)))

(defn csv-to-sites
  "Generates sites from a provider-set-id and a csv file"
  [store provider-set-id csv-file]
  (let [reader     (io/reader csv-file)
        sites-data (csv-data->maps (csv/read-csv reader))
        version    (:last-version (db-create-provider-set-version! (get-db store) {:id provider-set-id}))]
    (doall (map #(import-site store provider-set-id version %) sites-data))))

(defn sites-by-version
  "Returns sites associated to a provider-set-id and version"
  [store provider-set-id version]
  (db-find-sites (get-db store) {:provider-set-id provider-set-id
                                 :version version}))

;; ----------------------------------------------------------------------
;; Service definition

(defn create-provider-ser
  [store name owner-id coverage-algorithm]
  (db-create-provider-ser! (get-db store) {:name name
                                      :owner-id owner-id
                                      :coverage-algorithm (some-> coverage-algorithm clojure.core/name)}))

(defn list-providers-set
  [store owner-id]
  (db-list-providers-set (get-db store) {:owner-id owner-id}))

(defn get-provider-set
  [store provider-set-id]
  (db-find-provider-set (get-db store) {:id provider-set-id}))

(defn create-and-import-sites
  [store {:keys [name owner-id coverage-algorithm]} csv-file]
  (jdbc/with-db-transaction [tx (get-db store)]
    (let [tx-store (assoc-in store [:db :spec] tx)
          create-result (create-provider-set tx-store name owner-id coverage-algorithm)
          provider-set-id (:id create-result)]
      (csv-to-sites tx-store provider-set-id csv-file)
      (get-provider-set tx-store provider-set-id))))


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
  "Returns the initial job state to pre-process the providers-set' sites"
  [store provider-set-id]
  (let [db-spec      (get-db store)
        coverage     (:coverage store)
        provider-set      (db-find-provider-set db-spec {:id provider-set-id})
        last-version (:last-version provider-set)
        algorithm    (keyword (:coverage-algorithm provider-set))
        sites        (db-enum-site-ids db-spec {:provider-set-id provider-set-id :version last-version})
        options-list (coverage/enumerate-algorithm-options coverage algorithm)
        ;; TODO: configure the raster-dir in the component
        raster-dir   (str (io/file "data/coverage" (str provider-set-id)))]

    (cond
      (some? algorithm)
      {:store   store
       :options {:algorithm    algorithm
                 :options-list options-list
                 :raster-dir   raster-dir}
       :sites   sites}

      :else
      (do
        (warn "Coverage algorithm not set for provider-set" provider-set-id)
        nil))))

(defmethod jr/job-next-task ::boundary/preprocess-provider-set
  [[_ provider-set-id] {:keys [store options sites] :as state}]
  (let [next-site (first sites)
        sites'    (next sites)
        state'    (when sites' (assoc state :sites sites'))]
    (if (some? next-site)
      (let [site-id (:id next-site)]
        {:state state'
         :task-id site-id
         :task-fn (fn [] (preprocess-site! store site-id options))})
      {:state state'})))

(defn preprocess-provider-set!
  "Manually trigger the synchronous pre-processing of a provider-set"
  [store provider-set-id]
  (info "Pre-processing sites for provider-set" provider-set-id)
  (if-let [{:keys [sites options]} (new-processing-job store provider-set-id)]
    (dorun (for [site-id (map :id sites)]
             (preprocess-site! store site-id options)))
    (info "Coverage algorithm not set for provider-set" provider-set-id)))

(defn get-sites-with-coverage-in-region
  [store provider-set-id version filter-options]
  (let [db-spec   (get-db store)
        region-id (:region-id filter-options)
        algorithm (:coverage-algorithm filter-options)
        options   (:coverage-options filter-options)
        tags      (str/join " & " (:tags filter-options))]
    (db-find-sites-with-coverage-in-region db-spec {:provider-set-id provider-set-id
                                                    :version    version
                                                    :region-id  region-id
                                                    :algorithm  algorithm
                                                    :options    (some-> options pr-str)
                                                    :tags       tags})))

(defn count-sites-filter-by-tags
  ([store provider-set-id region-id tags]
   (count-sites-filter-by-tags store provider-set-id region-id tags nil))
  ([store provider-set-id region-id tags version]
   (let [db-spec  (get-db store)
         tags     (str/join " & " tags)
         count-fn (fn [tags version]
                    (let [{:keys [last-version]} (get-provider-set store provider-set-id)]
                      (:count (db-count-sites-with-tags db-spec {:provider-set-id provider-set-id
                                                                 :version         (or version last-version)
                                                                 :region-id       region-id
                                                                 :tags            tags}))))
         total     (count-fn "" version)
         response {:total total :filtered total}]
     (if (str/blank? tags) response (assoc response :filtered (count-fn tags version))))))

(defrecord ProvidersStore [db coverage]
  boundary/Providers-Set
  (list-providers-set [store owner-id]
    (list-providers-set store owner-id))
  (get-provider-set [store provider-set-id]
    (get-provider-set store provider-set-id))
  (create-and-import-sites [store options csv-file]
    (create-and-import-sites store options csv-file))
  (new-processing-job [store provider-set-id]
    (new-processing-job store provider-set-id))
  (get-sites-with-coverage-in-region [store provider-set-id version filter-options]
    (get-sites-with-coverage-in-region store provider-set-id version filter-options))
  (count-sites-filter-by-tags [store provider-set-id region-id tags]
    (count-sites-filter-by-tags store provider-set-id region-id tags))
  (count-sites-filter-by-tags [store provider-set-id region-id tags version]
    (count-sites-filter-by-tags store provider-set-id region-id tags version)))


(defmethod ig/init-key :planwise.component/providers-set
  [_ config]
  (map->ProvidersStore config))

(comment
  ;; REPL testing

  (def store (:planwise.component/providers-set integrant.repl.state/system))

  (get-sites-with-coverage-in-region store 19 1 {:region-id 42
                                                 :coverage-algorithm "pgrouting-alpha"
                                                 :coverage-options {:driving-time 60}})

  (preprocess-site! store 1 {:algorithm :simple-buffer
                             :options-list [{:distance 5} {:distance 10}]
                             :raster-dir "data/coverage/11"})

  (preprocess-site! store 1 {:algorithm :pgrouting-alpha
                             :options-list [{:driving-time 30} {:driving-time 60}]
                             :raster-dir "data/coverage/11"})

  (preprocess-provider-set! store 11))
