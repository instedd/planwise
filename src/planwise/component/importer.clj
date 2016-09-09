(ns planwise.component.importer
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.set :refer [rename-keys]]
            [planwise.model.import-job :as import-job]
            [planwise.component.resmap :as resmap]
            [planwise.component.projects :as projects]
            [planwise.component.facilities :as facilities]
            [planwise.boundary.datasets :as datasets]
            [planwise.component.taskmaster :as taskmaster]))

(timbre/refer-timbre)


;; ----------------------------------------------------------------------------
;; Import job task implementations

;; Field definition from resmap service
;; {:id "15890",
;;  :name "Type",
;;  :code "type",
;;  :kind "select_one",
;;  :config {:options [{:id 1, :code "hospital", :label "Hospital"}
;;                     {:id 2, :code "health center", :label "Health Center"}
;;                     {:id 3, :code "dispensary", :label "Dispensary"}],
;;           :next_id 4}}

(defn import-types
  "Import the facility types from a Resourcemap 'select-one' field definition."
  [facilities dataset-id type-field]
  (info (str "Dataset " dataset-id ": "
             "Importing facility types from Resourcemap field " (:id type-field)))
  (facilities/destroy-facilities! facilities dataset-id)
  (facilities/destroy-types! facilities dataset-id)
  (let [options (get-in type-field [:config :options])
        types (map (fn [{label :label}] {:name label}) options)
        codes (map :code options)
        inserted-types (facilities/insert-types! facilities dataset-id types)
        inserted-type-ids (map :id inserted-types)
        new-options (zipmap codes inserted-type-ids)]
    (info (str "Dataset " dataset-id ": "
               "Done importing " (count new-options) " facility types"))
    {:code (:code type-field)
     :options new-options}))

(defn with-location
  "Filter function for valid location"
  [{:keys [lat long], :as site}]
  (and lat long))

(defn facility-type-ctor
  "Returns a function which applied to a Resourcemap site returns the facility
  type according to the given type field (as returned from import-types)."
  [type-field]
  (let [field-name (:code type-field)
        options (:options type-field)
        type-path [:properties (keyword field-name)]]
    (fn [site]
      (let [f_type (get-in site type-path)]
        (get options f_type)))))

(defn site->facility-ctor
  "Returns a function to transform a Resourcemap site into a valid Facility
  using the facility type information given."
  [facility-type]
  (fn [site]
    (-> site
        (select-keys [:id :name :lat :long])
        (rename-keys {:id :site-id :long :lon})
        (assoc :type-id (facility-type site)))))

(defn sites->facilities
  "Filters and transforms a list of Resourcemap sites into a list of facilities
  using the given facility type field definition."
  [sites facility-type]
  (->> sites
       (filter with-location)
       (filter facility-type)
       (map (site->facility-ctor facility-type))))

(defn import-collection-page
  "Request a single page of sites from a Resourcemap collection and import the
  sites as facilities. Returns nil when Resourcemap returns no sites, or
  [:continue result]."
  [{:keys [resmap facilities]} user dataset-id coll-id type-field page]
  (info (str "Dataset " dataset-id ": "
             "Requesting page " page " of collection " coll-id " from Resourcemap"))
  (let [data (resmap/get-collection-sites resmap user coll-id {:page page})
        sites (:sites data)
        total-pages (:totalPages data)]
    (when (seq sites)
      (let [facility-type (facility-type-ctor type-field)
            new-facilities (sites->facilities sites facility-type)
            sites-without-location (filter (complement with-location) sites)
            sites-without-type (filter (complement facility-type) sites)]
        (info (str "Dataset " dataset-id ": "
                   "Inserting " (count new-facilities) " facilities from page " page
                   " of collection " coll-id))
        (let [new-ids (facilities/insert-facilities! facilities dataset-id new-facilities)]
          [:continue {:page-ids new-ids
                      :total-pages total-pages
                      :sites-without-location sites-without-location
                      :sites-without-type sites-without-type}])))))

(defn process-facilities
  [facilities facility-ids]
  (doall
    (map
      (fn [id]
        (info "Processing facility" id)
        (facilities/preprocess-isochrones facilities id))
      facility-ids)))

(defn update-projects
  [projects dataset-id]
  (info (str "Dataset " dataset-id ": Updating projects after importing facilities"))
  (let [list (projects/list-projects-for-dataset projects dataset-id)]
    (doseq [project list]
      (projects/update-project-stats projects project))))


;; ----------------------------------------------------------------------------
;; Job control

(defn try-accept-job
  [old-job new-job]
  (if (import-job/job-finished? old-job)
    new-job
    old-job))

(defn build-task-fn
  [{:keys [facilities projects resmap]} job task]
  (let [dataset-id (import-job/job-dataset-id job)]
    (case (import-job/task-type task)
      :import-types
      (let [type-field (import-job/job-type-field job)]
        (partial import-types facilities dataset-id type-field))

      :import-sites
      (let [services {:resmap resmap
                      :facilities facilities}
            user-ident (import-job/job-user-ident job)
            coll-id (import-job/job-collection-id job)
            type-field (import-job/job-type-field job)
            page (second task)]
        (partial import-collection-page services user-ident dataset-id coll-id type-field page))

      :process-facilities
      (let [facility-ids (second task)]
        (partial process-facilities facilities facility-ids))

      :update-projects
      (partial update-projects projects dataset-id))))


(defn jobs-next-task
  [jobs]
  (let [reducer-fn (fn [[jobs ready-job-id] [job-id job]]
                     (if (some? ready-job-id)
                       [(conj jobs [job-id job]) ready-job-id]
                       (let [job (import-job/next-task job)
                             task (when-not (import-job/job-finished? job)
                                    (import-job/job-peek-next-task job))]
                         [(conj jobs [job-id job]) (when (some? task) job-id)])))
        ;; remove previous ready job from the jobs map
        jobs (dissoc jobs ::ready-job-id)
        ;; find next ready job with a task to dispatch
        [jobs ready-job-id] (reduce reducer-fn [{} nil nil] jobs)]
    (assoc jobs ::ready-job-id ready-job-id)))

(defn finish-job
  [component job]
  (when (some? job)
    (let [result     (import-job/job-result job)
          stats      (import-job/job-stats job)
          dataset-id (import-job/job-dataset-id job)]
      (info (str "Import job for dataset " dataset-id " finished with result " result " (" stats ")"))
      (datasets/update-dataset (:datasets component)
                               {:id dataset-id
                                :import-result (assoc stats :result result)}))))

(defn jobs-finisher
  [component]
  (fn [jobs]
    (let [jobs (dissoc jobs ::ready-job-id)
          reducer-fn (fn [jobs [job-id job]]
                       (if (import-job/job-finished? job)
                         (do (finish-job component job) jobs)
                         (conj jobs [job-id job])))]
      (reduce reducer-fn {} jobs))))

;; ----------------------------------------------------------------------------
;; Service definition

(defrecord Importer [jobs taskmaster concurrent-workers resmap facilities datasets projects]
  component/Lifecycle
  (start [component]
    (info "Starting Importer component")
    (if-not (:taskmaster component)
      (let [jobs (atom {})
            concurrent-workers (or (:concurrent-workers component) 1)
            component (assoc component :jobs jobs)
            taskmaster (taskmaster/run-taskmaster component concurrent-workers)]
        (assoc component :taskmaster taskmaster))
      component))
  (stop [component]
    (info "Stopping Importer component")
    (when-let [taskmaster (:taskmaster component)]
      (taskmaster/quit taskmaster))
    (dissoc component :jobs :taskmaster))

  taskmaster/TaskDispatcher
  (next-task [component]
    (swap! (:jobs component) (jobs-finisher component))
    (let [jobs (swap! (:jobs component) jobs-next-task)
          ready-job-id (::ready-job-id jobs)]
      (if (some? ready-job-id)
        (let [ready-job (get jobs ready-job-id)
              task (import-job/job-peek-next-task ready-job)]
          (debug (str "Importer: next-task " task " for job " ready-job-id))
          {:task-id [ready-job-id task]
           :task-fn (build-task-fn component ready-job task)})
        (do
          (debug (str "Importer: no more tasks to execute"))
          nil))))

  (task-completed [component [job-id task-id] result]
    (debug (str "Importer: task " task-id " completed for job " job-id " with result: " result))
    (swap! (:jobs component) update job-id import-job/report-task-success task-id result)
    (swap! (:jobs component) (jobs-finisher component)))

  (task-failed [component [job-id task-id] error-info]
    (warn (str "Importer: task " task-id " failed for job " job-id " with: " error-info))
    (swap! (:jobs component) update job-id import-job/report-task-failure task-id error-info)
    (swap! (:jobs component) (jobs-finisher component))))

(defn importer
  "Constructs an Importer service"
  ([]
   (importer {}))
  ([config]
   (map->Importer config)))


;; Importer public API

(defn status
  "Retrieve the importer's current status"
  [service]
  (->> (dissoc @(:jobs service) ::ready-job-id)
       (map (fn [[job-id job]] [job-id (import-job/job-status job)]))
       (into {})))

(defn run-import-for-dataset
  [{:keys [datasets resmap] :as service} dataset-id user-ident]
  (let [dataset (datasets/find-dataset datasets dataset-id)]
    (if (some? dataset)
      (let [coll-id (:collection-id dataset)
            type-field-id (get-in dataset [:mappings :type])
            type-field (resmap/find-collection-field resmap user-ident coll-id type-field-id)
            new-job (import-job/create-job dataset-id user-ident coll-id type-field)
            jobs (swap! (:jobs service) update dataset-id try-accept-job new-job)
            accepted-job (get jobs dataset-id)]
        (if (= new-job accepted-job)
          (do
            (let [result (taskmaster/poll-dispatcher (:taskmaster service))]
              (when (= :error (first result))
                (throw (ex-info "Failure starting the import job" {:result result}))))
            [:ok (status service)])
          [:error :busy]))
      [:error :invalid-dataset])))

(defn cancel-import!
  [service job-id]
  (swap! (:jobs service) update job-id import-job/cancel-job)
  (status service))
