(ns planwise.component.importer
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.set :refer [rename-keys]]
            [planwise.model.import-job :as import-job]
            [planwise.component.resmap :as resmap]
            [planwise.component.projects :as projects]
            [planwise.component.facilities :as facilities]
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
  [facilities type-field]
  (info "Importing facility types from Resourcemap field" (:id type-field))
  (facilities/destroy-facilities! facilities)
  (facilities/destroy-types! facilities)
  (let [options (get-in type-field [:config :options])
        types (map (fn [{label :label}] {:name label}) options)
        codes (map :code options)
        inserted-types (facilities/insert-types! facilities types)
        inserted-type-ids (map :id inserted-types)
        new-options (zipmap codes inserted-type-ids)]
    (info "Done importing" (count new-options) "facility types")
    {:code (:code type-field)
     :options new-options}))

(defn sites-with-location
  "Filter Resourcemap sites which have a valid location"
  [sites]
  (filter #(and (:lat %) (:long %)) sites))

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
  [type-field]
  (let [facility-type (facility-type-ctor type-field)]
    (fn [site]
      (-> site
          (select-keys [:id :name :lat :long])
          (rename-keys {:long :lon})
          (assoc :type-id (facility-type site))))))

(defn sites->facilities
  "Filters and transforms a list of Resourcemap sites into a list of facilities
  using the given facility type field definition."
  [sites type-field]
  (->> sites
       (sites-with-location)
       (map (site->facility-ctor type-field))))

(defn import-collection-page
  "Request a single page of sites from a Resourcemap collection and import the
  sites as facilities. Returns nil when Resourcemap returns no sites, or
  [:continue ids] where ids are from the inserted facilities."
  [{:keys [resmap facilities]} user coll-id type-field page]
  (info (str "Requesting page " page " of collection " coll-id " from Resourcemap"))
  (let [data (resmap/get-collection-sites resmap user coll-id {:page page})
        sites (:sites data)]
    (when (seq sites)
      (info "Processing page" page "of collection" coll-id)
      (let [new-facilities (sites->facilities sites type-field)]
        (info "Inserting" (count new-facilities) "facilities")
        (facilities/insert-facilities! facilities new-facilities)
        [:continue (map :id new-facilities)]))))

(defn process-facilities
  [facilities facility-ids]
  (doseq [id facility-ids]
    (facilities/preprocess-isochrones facilities id)))

(defn update-projects
  [projects]
  (let [list (projects/list-projects projects)]
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
  (case (import-job/task-type task)
    :import-types
    (let [type-field (import-job/job-type-field job)]
      (partial import-types facilities type-field))

    :import-sites
    (let [services {:resmap resmap
                    :facilities facilities}
          user-ident (import-job/job-user-ident job)
          coll-id (import-job/job-collection-id job)
          type-field (import-job/job-type-field job)
          page (second task)]
      (partial import-collection-page services user-ident coll-id type-field page))

    :process-facilities
    (let [facility-ids (second task)]
      (partial process-facilities facilities facility-ids))

    :update-projects
    (partial update-projects projects)))

(defn log-job-status
  [job]
  (when (import-job/job-finished? job)
    (let [result (import-job/job-result job)]
      (info "Import job finished with result:" result))))


;; ----------------------------------------------------------------------------
;; Service definition

(defrecord Importer [job taskmaster resmap facilities projects]
  component/Lifecycle
  (start [component]
    (info "Starting Importer component")
    (if-not (:taskmaster component)
      (let [job (atom nil)
            component (assoc component :job job)
            taskmaster (taskmaster/run-taskmaster component)]
        (assoc component :taskmaster taskmaster))
      component))
  (stop [component]
    (info "Stopping Importer component")
    (when-let [taskmaster (:taskmaster component)]
      (taskmaster/quit taskmaster))
    (dissoc component :job :taskmaster))

  taskmaster/TaskDispatcher
  (next-task [component]
    (if-not (import-job/job-finished? @(:job component))
      (let [job (swap! (:job component) import-job/next-task)
            task (import-job/job-peek-next-task job)]
        (log-job-status job)
        (if (and (not (import-job/job-finished? job)) task)
          (do
            (info "Importer/next-task:" task)
            {:task-id task
             :task-fn (build-task-fn component job task)})
          (do
            (info "No more tasks to execute")
            nil)))
      (info "Nothing to do")))

  (task-completed [component task-id result]
    (info "Importer/task-completed" task-id result)
    (let [job (swap! (:job component) import-job/report-task-success task-id result)]
      (log-job-status job)))

  (task-failed [component task-id error-info]
    (info "Importer/task-failed" task-id error-info)
    (let [job (swap! (:job component) import-job/report-task-failure task-id error-info)]
      (log-job-status job))))

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
  (import-job/job-status @(:job service)))

(defn import-collection
  [service user-ident coll-id type-field]
  (let [new-job (import-job/create-job user-ident coll-id type-field)
        accepted-job (swap! (:job service) #(try-accept-job % new-job))]
    (if (= new-job accepted-job)
      (do
        (let [result (taskmaster/poll-dispatcher (:taskmaster service))]
          (when (= :error (first result))
            (throw (ex-info "Failure starting the import job" {:result result}))))
        (status service))
      [:error :busy])))

(defn cancel-import!
  [service]
  (swap! (:job service) import-job/cancel-job)
  (status service))
