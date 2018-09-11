(ns planwise.component.scenarios
  (:require [planwise.boundary.scenarios :as boundary]
            [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.sources :as sources]
            [planwise.boundary.engine :as engine]
            [planwise.boundary.jobrunner :as jr]
            [planwise.boundary.coverage :as coverage]
            [planwise.model.scenarios :as model]
            [clojure.string :as str]
            [planwise.util.str :as util-str]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.spec.alpha :as s]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/scenarios.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

(defn- sum-investments
  [changeset]
  (let [changeset (filter #(-> % :initial nil?) changeset)]
    (apply +' (map :investment changeset))))

(defn- build-changeset-summary
  [changeset]
  (let [providers (count (filter #(-> % :initial nil?) changeset))
        capacity (apply +' (mapv :capacity changeset))
        u (if (= providers 1) "provider" "providers")]
    (if (zero? providers) ""
        (format "Create %d %s. Increase overall capacity in %d." providers u capacity))))

(defn- map->csv
  [coll fields]
  (let [rows   (map #(map % fields) coll)
        data   (cons (mapv name fields) rows)]
    (with-out-str (csv/write-csv *out* data))))

;; ----------------------------------------------------------------------
;; Service definition

(defn get-scenario
  [store scenario-id]
  ;; TODO compute % coverage from initial scenario/projects
  (let [scenario (db-find-scenario (get-db store) {:id scenario-id})]
    (reduce (fn [map key] (update map key edn/read-string))
            scenario
            [:changeset :sources-data :providers-data :new-providers-geom])))

(defn- get-initial-providers-data
  [store project-id]
  (-> (db-get-initial-providers-data (get-db store) {:project-id project-id})
      :providers-data read-string))

(defn- get-initial-sources-data
  [store project-id]
  (-> (db-get-initial-sources-data (get-db store) {:project-id project-id})
      :sources-data read-string))

(defn- get-initial-providers
  [store provider-set-id version filter-options]
  (let [{:keys [providers disabled-providers]} (providers-set/get-providers-with-coverage-in-region
                                                (:providers-set store)
                                                provider-set-id
                                                version
                                                filter-options)
        select-fn (fn [{:keys [id name capacity lat lon]} key]
                    {key true
                     :provider-id id ;(str id)
                     :name name
                     :capacity capacity
                     :location {:lat lat :lon lon}})]
    {:providers (mapv #(select-fn % :initial) providers)
     :disabled-providers (mapv #(select-fn % :disabled) disabled-providers)}))

(defn- update-provider-data
  [provider updated-data]
  (let [id    (:provider-id provider)
        data  (dissoc (get updated-data id) :id)]
    (merge provider data)))

(defn- build-updated-data
  [providers-data]
  (reduce (fn [dic {:keys [id] :as provider}]
            (assoc dic id (dissoc provider :id)))
          {}
          providers-data))

(defn get-scenario-for-project
  [store scenario {:keys [provider-set-id provider-set-version config source-set-id] :as project}]
  (let [filter-options (-> (select-keys project [:region-id :coverage-algorithm])
                           (assoc :tags (get-in config [:providers :tags])
                                  :coverage-options (get-in config [:coverage :filter-options])))
        {:keys [providers disabled-providers]} (let [requested (select-keys scenario [:providers :disabled-providers])]
                                                 (if (empty? requested)
                                                   (get-initial-providers store provider-set-id provider-set-version filter-options)
                                                   requested))
        ; providers
        providers-data     (:providers-data scenario)
        updated-data       (build-updated-data providers-data)
        updated-providers  (map #(update-provider-data % updated-data)
                                providers)
        ;changeset
        updated-changeset  (map #(update-provider-data % updated-data) (:changeset scenario))
        ;sources
        sources             (sources/list-sources-in-set (:sources-set store) source-set-id)
        get-source-info-fn  (fn [id] (select-keys (-> (filter #(= id (:id %)) sources) first) [:name]))
        updated-sources (map (fn [s] (merge s (get-source-info-fn (:id s)))) (:sources-data scenario))]

    (-> scenario
        (assoc :providers updated-providers
               :disabled-providers disabled-providers
               :sources-data updated-sources
               :changeset updated-changeset)
        (dissoc :updated-at :providers-data :new-providers-geom))))

(defn get-provider-geom
  [store project scenario provider-id]
  (if (re-matches #"\A[0-9]+\z" provider-id)
    {:coverage-geom (:geom (providers-set/get-coverage (:providers-set store)
                                                       (Integer/parseInt provider-id)
                                                       {:algorithm (:coverage-algorithm project)
                                                        :filter-options (get-in project [:config :coverage :filter-options])
                                                        :region-id (:region-id project)}))}
    ((keyword provider-id) (:new-providers-geom scenario))))

(defn list-scenarios
  [store project-id]
  ;; TODO compute % coverage from initial scenario/project
  (let [list (db-list-scenarios (get-db store) {:project-id project-id})]
    (map (fn [{:keys [changeset] :as scenario}]
           (-> scenario
               (assoc  :changeset-summary (build-changeset-summary (read-string changeset)))
               (dissoc :changeset)))
         list)))

(defn create-initial-scenario
  [store project]
  (let [project-id  (:id project)
        scenario-id (:id (db-create-scenario! (get-db store)
                                              {:name            "Initial"
                                               :project-id      project-id
                                               :investment      0
                                               :demand-coverage nil
                                               :changeset       "[]"
                                               :label           "initial"}))]
    (jr/queue-job (:jobrunner store)
                  [::boundary/compute-initial-scenario scenario-id]
                  {:store store
                   :project project})
    scenario-id))

(defn- scenario-mark-as-error
  [store id exception]
  (db-mark-as-error (get-db store) {:id id
                                    :msg (pr-str (ex-data exception))}))

(defmethod jr/job-next-task ::boundary/compute-initial-scenario
  [[_ scenario-id] {:keys [store project] :as state}]
  (letfn [(task-fn []
            (info "Computing initial scenario" scenario-id)
            (try
              (let [engine (:engine store)
                    result (engine/compute-initial-scenario engine project)]
                (info "Initial scenario computed" result)
                ;; TODO check if scenario didn't change from result
                (db-update-scenario-state! (get-db store)
                                           {:id              scenario-id
                                            :raster          (:raster-path result)
                                            :demand-coverage (:covered-demand result)
                                            :providers-data  (pr-str (:providers-data result))
                                            :sources-data    (pr-str (:sources-data result))
                                            :new-providers-geom "{}"
                                            :state           "done"})
                (db-update-project-engine-config! (get-db store)
                                                  {:project-id    (:id project)
                                                   :engine-config (pr-str {:demand-quartiles           (:demand-quartiles result)
                                                                           :source-demand              (:source-demand result)
                                                                           :pending-demand-raster-path (:raster-path result)})}))
              (catch Exception e
                (scenario-mark-as-error store scenario-id e)
                (error "Scenario initial computation failed"))))]
    {:task-id :initial
     :task-fn task-fn
     :state   nil}))

(defn create-scenario
  [store project {:keys [name changeset]}]
  (assert (s/valid? ::model/change-set changeset))
  (let [changeset (map #(assoc % :provider-id (str (java.util.UUID/randomUUID))) changeset)
        result (db-create-scenario! (get-db store)
                                    {:name name
                                     :project-id (:id project)
                                     :investment (sum-investments changeset)
                                     :demand-coverage nil
                                     :changeset (pr-str changeset)
                                     :label nil})]
    (jr/queue-job (:jobrunner store)
                  [::boundary/compute-scenario (:id result)]
                  {:store store
                   :project project})
    result))

(defn update-scenario
  [store project {:keys [id name changeset error-message]}]
  ;; TODO assert scenario belongs to project
  (let [db (get-db store)
        project-id (:id project)
        label (:label (get-scenario store id))]
    (assert (s/valid? ::model/change-set changeset))
    (assert (not= label "initial"))
    (db-update-scenario! db
                         {:name name
                          :id id
                          :investment (sum-investments changeset)
                          :demand-coverage nil
                          :changeset (pr-str changeset)
                          :label nil})
        ;; Current label is removed so we need to search for the new optimal
    (db-update-scenarios-label! db {:project-id project-id})
    (jr/queue-job (:jobrunner store)
                  [::boundary/compute-scenario id]
                  {:store store
                   :project project})))

(defn- get-new-providers-geom
  [store scenario-id {:keys [provider-set-id provider-set-version config source-set-id] :as project}]
  (let [{:keys [new-providers-geom]} (db-get-new-providers-geom (get-db store) {:scenario-id scenario-id})]
    (when new-providers-geom (read-string new-providers-geom))))

(defmethod jr/job-next-task ::boundary/compute-scenario
  [[_ scenario-id] {:keys [store project] :as state}]
  (letfn [(task-fn []
            (info "Computing scenario" scenario-id)
            (try
              (let [engine            (:engine store)
                    scenario          (get-scenario store scenario-id)
                    initial-providers (get-initial-providers-data store (:project-id scenario))
                    initial-sources   (get-initial-sources-data store (:project-id scenario))
                    new-providers-geom (get-new-providers-geom store scenario-id project)
                    scenario-with-data (assoc scenario
                                              :providers-data initial-providers
                                              :sources-data initial-sources
                                              :new-providers-geom new-providers-geom)
                    result             (engine/compute-scenario engine project scenario-with-data)]
                (info "Scenario computed" result)
                ;; TODO check if scenario didn't change from result. If did, discard result.
                ;; TODO remove previous raster files
                (db-update-scenario-state! (get-db store)
                                           {:id              scenario-id
                                            :raster          (:raster-path result)
                                            :demand-coverage (:covered-demand result)
                                            :providers-data  (pr-str (:providers-data result))
                                            :sources-data    (pr-str (:sources-data result))
                                            :new-providers-geom (pr-str (:new-providers-geom result))
                                            :state           "done"})
                (db-update-scenarios-label! (get-db store) {:project-id (:id project)}))
              (catch Exception e
                (scenario-mark-as-error store scenario-id e)
                (error "Scenario computation failed" e))))]
    {:task-id scenario-id
     :task-fn task-fn
     :state   nil}))

;; private function to update the label based on investments and demand-coverage
;; will label of all scenarios of the project
(defn update-scenario-demand-coverage
  [store scenario-id demand-coverage]
  (let [db         (get-db store)
        scenario   (-> (db-find-scenario db scenario-id)
                       (update :demand-coverage demand-coverage))
        project-id (:project-id scenario)]
    (db-update-scenario! db scenario)
    (db-update-scenarios-label! db {:project-id project-id})))

(defn next-name-from-initial
  [store project-id]
  (util-str/next-alpha-name (:name (db-last-scenario-name (get-db store) {:project-id project-id}))))

(defn next-scenario-name
  [store project-id name]
  ;; Relies that initial scenario's name is "Initial"
  (if (= name "Initial") (next-name-from-initial store project-id)
      (->> (db-list-scenarios-names (get-db store) {:project-id project-id :name name})
           (map :name)
           (cons name)
           (util-str/next-name))))

(defn- disabled-providers-to-export
  [disabled-providers]
  (let [new-fields {:required-capacity 0 :used-capacity 0 :satisfied-demand 0 :unsatisfied-demand 0}]
    (mapv #(merge % new-fields) disabled-providers)))

(defn- changeset-to-export
  [changeset]
  (mapv (fn [{:keys [provider-id action capacity location]}]
          {:provider-id provider-id
           :type action
           :name ""
           :lat (:lat location)
           :lon (:lon location)
           :capacity capacity
           :tags ""}) changeset))

(defn- providers-to-export
  [store providers-data changeset disabled-providers]
  (let [update-fn (fn [{:keys [id] :as provider}]
                    (let [initial-data  (if (int? id)
                                          (providers-set/get-provider (:providers-set store) id)
                                          (-> (filter (fn [{:keys [provider-id]}] (= id provider-id)) changeset)
                                              (first)
                                              (assoc :id id)
                                              (dissoc :provider-id)))]
                      (merge initial-data
                             provider)))]
    (into (mapv (fn [e] (update-fn e)) providers-data)
          disabled-providers)))

(defn export-providers-data
  [store {:keys [provider-set-id config] :as project} scenario]
  (let [filter-options (-> (select-keys project [:region-id :coverage-algorithm])
                           (assoc :tags (get-in config [:providers :tags])
                                  :coverage-options (get-in config [:coverage :filter-options])))
        disabled-providers (disabled-providers-to-export
                            (:disabled-providers
                             (providers-set/get-providers-with-coverage-in-region
                              (:providers-set store)
                              provider-set-id
                              (:provider-set-version project)
                              filter-options)))
        changes        (changeset-to-export (:changeset scenario))
        fields [:id :type :name :lat :lon :tags :capacity :required-capacity :used-capacity :satisfied-demand :unsatisfied-demand]]
    (map->csv
     (providers-to-export store (:providers-data scenario) changes disabled-providers)
     fields)))

(defn reset-scenarios
  [store project-id]
  (db-delete-scenarios! (get-db store) {:project-id project-id})
  (engine/clear-project-cache (:engine store) project-id))

(defn get-provider-suggestion
  [store {:keys [sources-set-id] :as project} {:keys [raster sources-data] :as scenario}]
  (engine/search-optimal-location (:engine store) project  {:raster raster
                                                            :sources-data sources-data
                                                            :sources-set-id sources-set-id}))


(defrecord ScenariosStore [db engine jobrunner providers-set sources-set]
  boundary/Scenarios
  (list-scenarios [store project-id]
    (list-scenarios store project-id))
  (get-scenario [store scenario-id]
    (get-scenario store scenario-id))
  (create-initial-scenario [store project]
    (create-initial-scenario store project))
  (create-scenario [store project props]
    (create-scenario store project props))
  (update-scenario [store project props]
    (update-scenario store project props))
  (next-scenario-name [store project-id name]
    (next-scenario-name store project-id name))
  (reset-scenarios [store project-id]
    (reset-scenarios store project-id))
  (get-scenario-for-project [store scenario project]
    (get-scenario-for-project store scenario project))
  (export-providers-data [store project scenario]
    (export-providers-data store project scenario))
  (get-provider-suggestion [store project scenario]
    (get-provider-suggestion store project scenario))
  (get-provider-geom [store scenario project provider-id]
    (get-provider-geom store scenario project provider-id)))

(defmethod ig/init-key :planwise.component/scenarios
  [_ config]
  (map->ScenariosStore config))

(comment
  ;; REPL testing
  (def store (:planwise.component/scenarios integrant.repl.state/system))

  (list-scenarios store 1) ; project-id: 1
  (get-scenario store 2) ; scenario-id 2

  (def project (planwise.boundary.projects2/get-project (:planwise.component/projects2 integrant.repl.state/system) 5))

  (create-initial-scenario store project))

(comment
;;REPL testing
  (def store (:planwise.component/scenarios integrant.repl.state/system))

  (def initial-scenario (get-scenario store 240)) ; scenario-id: 240 label: initial project-id: 16
  (def scenario         (get-scenario store 244)); scenario-id: 244 project-id: 16

  (def initial-demand   (:demand-coverage scenario))
  (def final-demand     (:demand-coverage initial-scenario))

  (def initial-providers     (read-string (:providers-data initial-scenario)))
  (def providers-and-changes (read-string (:providers-data scenario)))
  (def changes  (subvec providers-and-changes (count initial-providers)))

  (def capacity-sat    (Math/abs (- initial-demand final-demand)))
  (>= (reduce + (mapv :satisfied changes)) capacity-sat))
