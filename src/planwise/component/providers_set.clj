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
            [planwise.util.files :as files]
            [clojure.string :as str]
            [clojure.set :as set]))

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

(defn- import-provider
  [store provider-set-id version csv-provider-data]
  (let [data {:source-id (Integer. (:id csv-provider-data))
              :type (:type csv-provider-data)
              :version version
              :provider-set-id provider-set-id
              :name (:name csv-provider-data)
              :lat  (Double. (:lat csv-provider-data))
              :lon  (Double. (:lon csv-provider-data))
              :capacity (Integer. (:capacity csv-provider-data))
              :tags (:tags csv-provider-data)}]
    (db-create-provider! (get-db store) data)))

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
  [store name owner-id coverage-algorithm]
  (db-create-provider-set! (get-db store) {:name name
                                           :owner-id owner-id
                                           :coverage-algorithm (some-> coverage-algorithm clojure.core/name)}))

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
  [store {:keys [name owner-id coverage-algorithm]} csv-file]
  (jdbc/with-db-transaction [tx (get-db store)]
    (let [tx-store (assoc-in store [:db :spec] tx)
          create-result (create-provider-set tx-store name owner-id coverage-algorithm)
          provider-set-id (:id create-result)]
      (csv-to-providers tx-store provider-set-id csv-file)
      (get-provider-set tx-store provider-set-id))))


;;;
;;; Pre-processing job implementation
;;;

(defn compute-provider-coverage!
  [store provider {:keys [algorithm options raster-dir]}]
  (try
    (let [db-spec         (get-db store)
          coverage        (:coverage store)
          coords          (select-keys provider [:lat :lon])
          provider-id         (:id provider)
          raster-basename (str/join "_" (flatten [provider-id (name algorithm) (vals options)]))
          raster-filename (str raster-basename ".tif")
          raster-path     (str (io/file raster-dir raster-filename))
          criteria        (merge {:algorithm algorithm
                                  :raster    raster-path}
                                 options)
          polygon         nil ;; FIXME: remove all this cruft (coverage/compute-coverage coverage coords criteria)
          provider-coverage   {:provider-id   provider-id
                               :algorithm (name algorithm)
                               :options   (pr-str options)
                               :geom      polygon
                               :raster    raster-basename}
          result          (db-create-provider-coverage! db-spec provider-coverage)]
      {:ok (:id result)})
    (catch RuntimeException e
      (warn "Error" (.getMessage e) "processing coverage for provider" (:id provider)
            "using algorithm" algorithm "with options" (pr-str options))
      {:error (.getMessage e)})))

(defn preprocess-provider!
  [store provider-id {:keys [algorithm options-list raster-dir]}]
  {:pre [(some? algorithm)]}
  (let [db-spec    (get-db store)
        provider       (db-fetch-provider-by-id db-spec {:id provider-id})]
    (if (nil? (:processing-status provider))
      (do
        (info "Pre-processing provider" provider-id)
        ;; TODO: delete old raster as well as the database records
        (db-delete-algorithm-coverages-by-provider-id! db-spec {:provider-id provider-id :algorithm (name algorithm)})
        (let [results (doall (for [options options-list]
                               (compute-provider-coverage! store provider {:algorithm algorithm
                                                                           :options options
                                                                           :raster-dir raster-dir})))]
          (let [total     (count options-list)
                succeeded (count (filter (comp some? :ok) results))
                result    (condp = succeeded
                            total :ok
                            0 :error
                            :partial)]
            (db-update-provider-processing-status! db-spec {:id provider-id
                                                            :processing-status (str result)}))))
      (info "Skipping provider" provider-id
            "since it's already processed with status" (:processing-status provider)))))

(defn new-processing-job
  "Returns the initial job state to pre-process the providers-set' providers"
  [store provider-set-id]
  (let [db-spec      (get-db store)
        coverage     (:coverage store)
        provider-set      (db-find-provider-set db-spec {:id provider-set-id})
        last-version (:last-version provider-set)
        algorithm    (keyword (:coverage-algorithm provider-set))
        providers        (db-enum-provider-ids db-spec {:provider-set-id provider-set-id :version last-version})
        options-list (coverage/enumerate-algorithm-options coverage algorithm)
        ;; TODO: configure the raster-dir in the component
        raster-dir   (str (io/file "data/coverage" (str provider-set-id)))]

    (cond
      (some? algorithm)
      {:store   store
       :options {:algorithm    algorithm
                 :options-list options-list
                 :raster-dir   raster-dir}
       :providers   providers}

      :else
      (do
        (warn "Coverage algorithm not set for provider-set" provider-set-id)
        nil))))

(defmethod jr/job-next-task ::boundary/preprocess-provider-set
  [[_ provider-set-id] {:keys [store options providers] :as state}]
  (let [next-provider (first providers)
        providers'    (next providers)
        state'    (when providers' (assoc state :providers providers'))]
    (if (some? next-provider)
      (let [provider-id (:id next-provider)]
        {:state state'
         :task-id provider-id
         :task-fn (fn [] (preprocess-provider! store provider-id options))})
      {:state state'})))

(defn preprocess-provider-set!
  "Manually trigger the synchronous pre-processing of a provider-set"
  [store provider-set-id]
  (info "Pre-processing providers for provider-set" provider-set-id)
  (if-let [{:keys [providers options]} (new-processing-job store provider-set-id)]
    (dorun (for [provider-id (map :id providers)]
             (preprocess-provider! store provider-id options)))
    (info "Coverage algorithm not set for provider-set" provider-set-id)))

(defn- provider-matches-tags?
  [provider tags]
  (let [filter-tags (set tags)
        provider-tags (set (str/split (:tags provider) #" "))]
    (or (empty? tags)
        (not (empty? (set/intersection filter-tags provider-tags))))))

(defn get-providers-with-coverage-in-region
  [store provider-set-id version filter-options]
  (let [db-spec   (get-db store)
        config {:provider-set-id provider-set-id
                :version    version
                :region-id  (:region-id filter-options)
                :algorithm  (:coverage-algorithm filter-options)
                :options    (some-> (:coverage-options filter-options) pr-str)}
        all-providers (db-find-providers-with-coverage-in-region db-spec config)
        providers-partition (group-by
                             #(provider-matches-tags? % (:tags filter-options))
                             all-providers)]
    {:providers (or (get providers-partition true) [])
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

(defn get-radius-from-computed-coverage
  [store {:keys [algorithm] :as criteria} provider-set-id]
  (let [options (dissoc criteria :algorithm)]
    {:avg-max (:avg (db-avg-max-distance (get-db store) {:algorithm (name algorithm)
                                                         :provider-set-id provider-set-id
                                                         :options (str options)}))}))

(defn get-coverage
  [store provider-id {:keys [algorithm region-id filter-options]}]
  (db-find-provider-coverage (get-db store) {:provider-id provider-id
                                             :algorithm algorithm
                                             :options (pr-str filter-options)
                                             :region-id region-id}))

(defn delete-provider-set
  [store provider-set-id]
  (try
    (jdbc/with-db-transaction [tx (get-db store)]
      (let [tx-store        (assoc-in store [:db :spec] tx)
            params          {:provider-set-id provider-set-id}]
        (db-delete-providers-coverage! tx params)
        (db-delete-providers! tx params)
        (db-delete-provider-set! tx params)))
    (files/delete-files-recursively
     (str "data/coverage/" provider-set-id)
     true)
    (catch Exception e
      (throw (ex-info "Provider set can not be deleted"
                      {:provider-set-id provider-set-id}
                      e)))))


(defrecord ProvidersStore [db coverage]
  boundary/ProvidersSet
  (list-providers-set [store owner-id]
    (list-providers-set store owner-id))
  (get-provider-set [store provider-set-id]
    (get-provider-set store provider-set-id))
  (get-provider [store provider-id]
    (get-provider store provider-id))
  (create-and-import-providers [store options csv-file]
    (create-and-import-providers store options csv-file))
  (new-processing-job [store provider-set-id]
    (new-processing-job store provider-set-id))
  (get-providers-with-coverage-in-region [store provider-set-id version filter-options]
    (get-providers-with-coverage-in-region store provider-set-id version filter-options))
  (count-providers-filter-by-tags [store provider-set-id region-id tags]
    (count-providers-filter-by-tags store provider-set-id region-id tags))
  (count-providers-filter-by-tags [store provider-set-id region-id tags version]
    (count-providers-filter-by-tags store provider-set-id region-id tags version))
  (get-radius-from-computed-coverage [store criteria provider-set-id]
    (get-radius-from-computed-coverage store criteria provider-set-id))
  (get-coverage [store provider-id coverage-options]
    (get-coverage store provider-id coverage-options))
  (delete-provider-set [store provider-set-id]
    (delete-provider-set store provider-set-id)))

(defmethod ig/init-key :planwise.component/providers-set
  [_ config]
  (map->ProvidersStore config))

(comment
  ;; REPL testing

  (def store (:planwise.component/providers-set integrant.repl.state/system))

  (get-providers-with-coverage-in-region store 19 1 {:region-id 42
                                                     :coverage-algorithm "driving-friction"
                                                     :coverage-options {:driving-time 60}})

  (preprocess-provider! store 1 {:algorithm :simple-buffer
                                 :options-list [{:distance 5} {:distance 10}]
                                 :raster-dir "data/coverage/11"})

  (preprocess-provider-set! store 11))
