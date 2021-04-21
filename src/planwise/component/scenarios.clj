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
            [planwise.model.providers :refer [merge-providers]]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [planwise.common :as common]))

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

(def action-labels {"create-provider" "create" "upgrade-provider" "upgrade" "increase-provider" "increase capacity for"})

(defn- build-summary
  [counters provider-unit]
  (letfn [(format-action
            ([action]
             (format-action action false))
            ([[label count] final]
             (str label " " (if final (str count " " provider-unit) count))))
          (format-summary
            ([] "")
            ([x] (format-action x true))
            ([x y] (str (format-action x) " and " (format-action y true)))
            ([x y z] (str (format-action x) ", " (format-action y) " and " (format-action z true))))
          (build-action
            [[action label] counters]
            (let [count (get counters action 0)]
              (if (pos? count) [label count])))]
    (->> action-labels
         (map #(build-action % counters))
         (filter some?)
         (apply format-summary)
         (str/capitalize))))

(defn- build-changeset-summary
  [changeset {:keys [provider-unit capacity-unit]}]
  (let [changeset (filter #(-> % :initial nil?) changeset)
        counters (frequencies (map :action changeset))
        providers (count changeset)
        capacity (apply +' (mapv :capacity changeset))
        summary (build-summary counters provider-unit)]
    (if (zero? providers) ""
        (format "%s. Increase overall capacity in %d %s." summary capacity capacity-unit))))

(defn- map->csv
  [coll fields]
  (let [rows   (map #(map % fields) coll)
        data   (cons (mapv name fields) rows)]
    (with-out-str (csv/write-csv *out* data))))

(defn remove-unused-scenario-files
  [{:keys [id raster] :as scenario} scenario-result-after-computation]
  (when (some? raster)
    (io/delete-file (io/file (str "data/" raster ".tif")) :silent)
    (io/delete-file (io/file (str "data/" raster ".map.tif")) :silent)
    (io/delete-file (io/file (str "data/" raster ".coverage.tif")) :silent)
    (io/delete-file (io/file (str "data/" raster ".base-demand.tif")) :silent)))

;; ----------------------------------------------------------------------
;; Service definition

(defn- map-scenario
  [scenario]
  (reduce (fn [map key] (update map key edn/read-string))
          scenario
          [:changeset :sources-data :providers-data :new-providers-geom]))

(defn get-scenario
  [store scenario-id]
  ;; TODO compute % coverage from initial scenario/projects
  (let [scenario (db-find-scenario (get-db store) {:id scenario-id})]
    (when scenario (map-scenario scenario))))

(defn delete-scenario
  [store scenario-id]
  (let [{:keys [project-id raster] :as scenario} (get-scenario store scenario-id)]
    (try
      (db-delete-scenario! (get-db store) {:id scenario-id})
      (when raster
        (remove-unused-scenario-files scenario {}))
      (catch Exception e
        (ex-info "Can not delete current scenario" {:id scenario-id} e)))))

(defn get-initial-scenario
  [store project-id]
  (let [scenario (db-find-initial-scenario (get-db store) {:project-id project-id})]
    (map-scenario scenario)))

(defn- get-initial-providers
  [store provider-set-id version filter-options]
  (let [{:keys [providers disabled-providers]} (providers-set/get-providers-in-region
                                                (:providers-set store)
                                                provider-set-id
                                                version
                                                filter-options)
        mapper-fn (fn [{:keys [id name capacity lat lon]}]
                    {:id       id
                     :name     name
                     :capacity capacity
                     :location {:lat lat :lon lon}})]
    {:providers          (map mapper-fn providers)
     :disabled-providers (map mapper-fn disabled-providers)}))

(defn get-scenario-for-project
  [store scenario {:keys [provider-set-id provider-set-version config source-set-id] :as project}]
  (let [filter-options                         {:region-id (:region-id project)
                                                :tags      (get-in project [:config :providers :tags])}
        {:keys [providers disabled-providers]} (let [requested (select-keys scenario [:providers :disabled-providers])]
                                                 (if (empty? requested)
                                                   (get-initial-providers store provider-set-id provider-set-version filter-options)
                                                   requested))
                                        ;sources
        sources                                (sources/list-sources-in-set (:sources-set store) source-set-id)
        get-source-info-fn                     (fn [id] (select-keys (-> (filter #(= id (:id %)) sources) first) [:name]))
        updated-sources                        (map (fn [s] (merge s (get-source-info-fn (:id s)))) (:sources-data scenario))]

    (-> scenario
        (assoc :sources-data updated-sources
               :providers providers
               :disabled-providers disabled-providers
               :source-demand (get-in project [:engine-config :source-demand]))
        (dissoc :updated-at :new-providers-geom))))

(defn get-provider-geom
  [store project scenario id]
  (let [context-id   [:project (:id project)]
        id           (if (re-matches #"\A[0-9]+\z" id) (Long/parseLong id) id)
        query-id     [:provider id]
        query-result (first (coverage/query-coverages (:coverage store) context-id :geojson [query-id]))]
    (when (:resolved query-result)
      {:coverage-geom (:geojson query-result)})))

(defn list-scenarios
  [store project]
  ;; TODO compute % coverage from initial scenario/project
  (let [project-id    (:id project)
        provider-unit (common/get-provider-unit project)
        capacity-unit (common/get-capacity-unit project)
        list          (db-list-scenarios (get-db store) {:project-id project-id})]
    (map (fn [{:keys [changeset] :as scenario}]
           (-> scenario
               (assoc  :changeset-summary (build-changeset-summary (edn/read-string changeset) {:provider-unit provider-unit :capacity-unit capacity-unit}))
               (dissoc :changeset)))
         list)))

(defn create-initial-scenario
  [store project]
  (let [project-id  (:id project)
        scenario-id (:id (db-create-scenario! (get-db store)
                                              {:name            "Initial"
                                               :project-id      project-id
                                               :effort          0
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

(defn compute-initial-scenario
  [scenario-id {:keys [store project]}]
  (info "Computing initial scenario " scenario-id)
  (try
    (let [engine    (:engine store)
          scenario  (get-scenario store scenario-id)
          result    (engine/compute-initial-scenario engine project)]
      (info (str "Initial scenario " scenario-id " computed")

            (select-keys result [:raster-path :geo-coverage :population-under-coverage :covered-demand :providers-data :sources-data]))
      (info (str "Computed geographic coverage " (* 100 (:geo-coverage result 0))))
      ;; TODO check if scenario didn't change from result
      (db-update-scenario-state! (get-db store)
                                 {:id                         scenario-id
                                  :raster                     (:raster-path result)
                                  :demand-coverage            (:covered-demand result)
                                  :providers-data             (pr-str (:providers-data result))
                                  :sources-data               (pr-str (:sources-data result))
                                  :new-providers-geom         (pr-str {})
                                  :geo-coverage               (:geo-coverage result)
                                  :population-under-coverage  (:population-under-coverage result)
                                  :state                      "done"})
      (remove-unused-scenario-files scenario result)
      (db-update-project-engine-config! (get-db store)
                                        {:project-id    (:id project)
                                         :engine-config (pr-str {:demand-quartiles           (:demand-quartiles result)
                                                                 :source-demand              (:source-demand result)
                                                                 :pending-demand-raster-path (:raster-path result)
                                                                 ;; only relevant for raster scenarios
                                                                 :raster-resolution          (:raster-resolution result)
                                                                 :scaling-factor             (:scaling-factor result)})}))
    (catch Exception e
      (scenario-mark-as-error store scenario-id e)
      (error e "Scenario initial computation failed"))))


(defmethod jr/job-next-task ::boundary/compute-initial-scenario
  [[_ scenario-id] state]
  (letfn [(task-fn []
            (compute-initial-scenario scenario-id state))]

    {:task-id :initial
     :task-fn task-fn
     :state   nil}))

(defn create-provider-new-id-when-necessary
  [provider]
  (if (= (:action provider) "create-provider")
    (assoc provider :id (str (java.util.UUID/randomUUID)))
    provider))

(defn- calc-effort
  [analysis-type changeset]
  (if (common/is-budget analysis-type) (sum-investments changeset) (count changeset)))

(defn create-scenario
  [store project {:keys [name changeset]}]
  (assert (s/valid? ::model/change-set changeset))
  (let [changeset (map create-provider-new-id-when-necessary changeset)
        analysis-type (get-in project [:config :analysis-type])
        effort (calc-effort analysis-type changeset)
        result (db-create-scenario! (get-db store)
                                    {:name name
                                     :project-id (:id project)
                                     :effort effort
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
        analysis-type (get-in project [:config :analysis-type])
        effort (calc-effort analysis-type changeset)
        label (:label (get-scenario store id))]
    (assert (s/valid? ::model/change-set changeset))
    (assert (not= label "initial"))
    (db-update-scenario! db
                         {:name name
                          :id id
                          :effort effort
                          :demand-coverage nil
                          :changeset (pr-str changeset)
                          :label nil})
        ;; Current label is removed so we need to search for the new optimal
    (db-update-scenarios-label! db {:project-id project-id})
    (jr/queue-job (:jobrunner store)
                  [::boundary/compute-scenario id]
                  {:store store
                   :project project})))

(defn compute-scenario
  [scenario-id {:keys [store project]}]
  (info "Computing scenario" scenario-id)
  (try
    (let [engine           (:engine store)
          scenario         (get-scenario store scenario-id)
          initial-scenario (get-initial-scenario store (:id project))
          result           (engine/compute-scenario engine project initial-scenario scenario)]
      (info (str "Scenario " scenario-id " computed")
            (select-keys result [:raster-path :geo-coverage :population-under-coverage :covered-demand :providers-data :sources-data]))
      (info (str "Computed geographic coverage " (* 100 (:geo-coverage result 0))))
      ;; TODO check if scenario didn't change from result. If did, discard result.
      (db-update-scenario-state! (get-db store)
                                 {:id                         scenario-id
                                  :raster                     (:raster-path result)
                                  :demand-coverage            (:covered-demand result)
                                  :providers-data             (pr-str (:providers-data result))
                                  :sources-data               (pr-str (:sources-data result))
                                  :new-providers-geom         (pr-str (:new-providers-geom result))
                                  :geo-coverage               (:geo-coverage result)
                                  :population-under-coverage  (:population-under-coverage result)
                                  :state                      "done"})
      (remove-unused-scenario-files scenario result)
      (db-update-scenarios-label! (get-db store) {:project-id (:id project)}))
    (catch Exception e
      (scenario-mark-as-error store scenario-id e)
      (error e "Scenario computation failed"))))

(defmethod jr/job-next-task ::boundary/compute-scenario
  [[_ scenario-id] state]
  (letfn [(task-fn []
            (compute-scenario scenario-id state))]
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

(defn- created-provider-to-export
  [{:keys [id action capacity location name]}]
  {:id id
   :type action
   :name (or name "New")
   :lat (:lat location)
   :lon (:lon location)
   :capacity capacity
   :tags ""})

(defn- new-providers-to-export
  [changeset]
  (map created-provider-to-export (filter #(= (:action %) "create-provider") changeset)))

(defn export-providers-data
  [store {:keys [provider-set-id config] :as project} scenario]
  (let [filter-options                         {:region-id (:region-id project)
                                                :tags      (get-in project [:config :providers :tags])}
        {:keys [disabled-providers providers]} (providers-set/get-providers-in-region
                                                (:providers-set store)
                                                provider-set-id
                                                (:provider-set-version project)
                                                filter-options)
        disabled-providers                     (map #(assoc % :capacity 0) disabled-providers)
        new-providers                          (new-providers-to-export (:changeset scenario))
        fields                                 [:id :type :name :lat :lon :tags :capacity :required-capacity :used-capacity :satisfied-demand :unsatisfied-demand]]
    (map->csv
     (merge-providers providers disabled-providers new-providers (:providers-data scenario))
     fields)))

(defn reset-scenarios
  [store project-id]
  (db-delete-scenarios! (get-db store) {:project-id project-id})
  (engine/clear-project-cache (:engine store) project-id))

(defn get-suggestions-for-new-provider-location
  [store project {:keys [raster sources-data] :as scenario}]
  (engine/search-optimal-locations (:engine store) project  scenario))

(defn- get-current-investment
  [changeset]
  (reduce + (map :investment changeset)))

(defn get-suggestions-for-improving-providers
  [store {:keys [sources-set-id config] :as project} {:keys [raster sources-data] :as scenario}]
  (let [increasing-costs (sort-by :capacity (get-in config [:actions :upgrade]))
        upgrade-budget   (get-in config [:actions :upgrade-budget])
        analysis-type    (get-in config [:analysis-type])
        costs-config?    (and (not (empty? increasing-costs)) (some? upgrade-budget))
        settings         (merge
                          {:analysis-type analysis-type
                           :available-budget (if (common/is-budget analysis-type)
                                               (- (get-in config [:actions :budget])
                                                  (get-current-investment (:changeset scenario)))
                                               0)}
                          (if costs-config?
                            {:max-capacity     (:capacity (last increasing-costs))
                             :increasing-costs increasing-costs
                             :upgrade-budget   (get-in config [:actions :upgrade-budget])}
                            {:no-action-costs true}))
        show-keys (conj [:id :action-capacity] (when costs-config? :action-cost))]
    (take 5
          (map
           #(select-keys % show-keys)
           (engine/search-optimal-interventions (:engine store) project scenario settings)))))

(defn compute-scenario-stats
  [scenario-id {:keys [store project]}]
  (info "Computing scenario statistics" scenario-id)
  (try
    (let [engine           (:engine store)
          scenario         (get-scenario store scenario-id)
          params           [:some :params]
          result           (engine/compute-scenario-stats engine project scenario params)]
      (info (str "Scenario " scenario-id " statistics computed")))
    (catch Exception e
      (error e "Scenario statistics computation failed"))))

(defmethod jr/job-next-task ::boundary/compute-scenario-stats
  [[_ scenario-id] state]
  (info "Job next task - Computing scenario statistics" scenario-id)
  (letfn [(task-fn []
            (compute-scenario-stats scenario-id state))]
    {:task-id scenario-id
     :task-fn task-fn
     :state   nil}))


(defrecord ScenariosStore [db engine jobrunner providers-set sources-set coverage]
  boundary/Scenarios
  (list-scenarios [store project]
    (list-scenarios store project))
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
  (get-suggestions-for-new-provider-location [store project scenario]
    (get-suggestions-for-new-provider-location store project scenario))
  (get-provider-geom [store scenario project provider-id]
    (get-provider-geom store scenario project provider-id))
  (delete-scenario [store scenario-id]
    (delete-scenario store scenario-id))
  (get-suggestions-for-improving-providers [store project scenario]
    (get-suggestions-for-improving-providers store project scenario)))

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

  (def initial-providers     (edn/read-string (:providers-data initial-scenario)))
  (def providers-and-changes (edn/read-string (:providers-data scenario)))
  (def changes  (subvec providers-and-changes (count initial-providers)))

  (def capacity-sat    (Math/abs (- initial-demand final-demand)))
  (>= (reduce + (mapv :satisfied changes)) capacity-sat))
