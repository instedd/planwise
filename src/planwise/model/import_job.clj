(ns planwise.model.import-job
  (:require [reduce-fsm :as fsm]
            [schema.core :as s]
            [clojure.core.match :refer [match]]))


;; Import job tasks

(s/defschema ImportTask
  "Task identification values"
  (s/conditional
   #(= % :import-types)
   (s/eq :import-types)

   #(and (coll? %) (= (first %) :import-sites))
   (s/pair (s/eq :import-sites) "type" s/Int "page")

   #(and (coll? %) (= (first %) :process-facilities))
   (s/pair (s/eq :process-facilities) "type" [s/Int] "ids")

   #(= % :update-projects)
   (s/eq :update-projects)))

(defn task-type
  [task-id]
  (cond
    (keyword? task-id) task-id
    (coll? task-id) (first task-id)
    true nil))


;; Task queue functions

(defn push-task
  [tasks task]
  (if (and (some? task) (not= task (peek tasks)))
    (conj tasks task)
    tasks))

(defn remove-task
  [tasks task]
  (->> tasks
       (remove #(= task %))
       vec))


;; FSM value update functions

(defn clear-dispatch
  [job & _]
  (assoc job :next-task nil))

(defn dispatch-task
  [job task]
  (-> job
      (update :tasks push-task task)
      (assoc :next-task task)))

(defn cancel-import
  [job & _]
  (-> (assoc job :result :cancelled)
      (clear-dispatch)))

(defn unexpected-event
  [job evt state & _]
  (assoc job
         :result :unexpected-event
         :event evt
         :last-state state))

(defn complete-task
  [job [_ task _] & _]
  (-> job
      (update :tasks remove-task task)
      (clear-dispatch)))

(defn dispatch-import-types
  [job & _]
  (dispatch-task job :import-types))

(defn import-types-succeeded
  [job event & _]
  (let [[_ _ data] event]
    (-> (complete-task job event)
        (assoc :type-field data))))

(defn import-types-failed
  [job event & _]
  (let [[_ _ error] event]
    (-> (complete-task job event)
        (assoc :result :import-types-failed
               :error-info error))))

(defn dispatch-import-sites
  [job & _]
  (let [page (:page job)]
    (dispatch-task job [:import-sites page])))

(defn import-sites-succeeded
  [job event & _]
  (let [page (:page job)
        [_ _ [_ {:keys [page-ids total-pages sites-without-location sites-without-type]}]] event
        {:keys [new existing moved updated]} page-ids
        total-pages (or total-pages (:page-count job))]
    (-> (complete-task job event)
        (assoc :page (inc page)
               :page-count total-pages)
        (update :facility-ids into (apply concat (vals page-ids)))
        (update :facility-count + (apply + (map count (vals page-ids))))
        (update :process-ids concat new moved)
        (update :process-count + (count new) (count moved))
        (update :sites-without-location-count + (count sites-without-location))
        (update :sites-without-type-count + (count sites-without-type)))))

(defn import-sites-failed
  [job event & _]
  (let [[_ _ error] event]
    (-> (complete-task job event)
        (assoc :result :import-sites-failed
               :error-info error))))

(defn dispatch-delete-old-facilities
  [job & _]
  (dispatch-task job :delete-old-facilities))

(defn delete-old-facilities-succeeded
  [job event & _]
  (complete-task job event))

(defn delete-old-facilities-failed
  [job event & _]
  (let [[_ _ error] event]
    (-> (complete-task job event)
        (assoc :result :delete-old-facilities-failed
                       :error-info error))))

(defn dispatch-delete-old-types
  [job & _]
  (dispatch-task job :delete-old-types))

(defn delete-old-types-succeeded
  [job event & _]
  (complete-task job event))

(defn delete-old-types-failed
  [job event & _]
  (let [[_ _ error] event]
    (-> (complete-task job event)
        (assoc :result :delete-old-types-failed
                       :error-info error))))

(defn dispatch-process-facilities
  [job & _]
  (let [process-ids (:process-ids job)
        next-facility (first process-ids)]
    (if (nil? next-facility)
      (clear-dispatch job)
      (-> job
          (dispatch-task [:process-facilities [next-facility]])
          (update :process-ids rest)))))

(defn dispatch-update-projects
  [job & _]
  (dispatch-task job :update-projects))

(defn update-projects-succeeded
  [job event & _]
  (complete-task job event))

(defn update-projects-failed
  [job event & _]
  (let [[_ _ error] event]
    (-> (complete-task job event)
        (assoc :result :update-projects-failed
               :error-info error))))

(defn- update-facilities-counts [job event]
  (let [[_ _ results] event
        results-counts (frequencies results)]
    (-> job
      (update :facilities-without-road-network-count + (get results-counts :no-road-network 0))
      (update :facilities-outside-regions-count + (get results-counts :outside-regions 0)))))

(defn complete-processing
  [job event & _]
  (-> job
    (update-facilities-counts event)
    (complete-task event)))

(defn last-complete-processing
  [job event & _]
  (-> job
    (complete-processing event)
    (assoc :result :success)))

(defn nothing-to-process
  [job event & _]
  (-> job
      (assoc :result :success)
      (clear-dispatch)))

;; FSM guards

(defn task-report?
  [event]
  (and (coll? event) (case (first event) (:success :failure) true false)))

(defn event-task-report?
  [[_ event]]
  (task-report? event))

(defn last-task-report?
  [[{tasks :tasks} event]]
  (and (task-report? event)
       (or (empty? tasks)
           (and (= 1 (count tasks))
                (= (first tasks) (second event))))))

(defn page-number-mismatch?
  [[{page :page} event]]
  (when (task-report? event)
    (let [[_ task _] event
          event-page (match task [:import-sites p] p :else :no-match)]
      (and (number? event-page)
           (not= page event-page)))))

(defn no-pending-tasks?
  [{tasks :tasks}]
  (empty? tasks))

(defn process-report?
  [event]
  (and (task-report? event)
       (= :process-facilities (first (second event)))))

(defn done-processing?
  [[job event]]
  (and (empty? (:process-ids job))
       (process-report? event)
       (last-task-report? [job event])))

(defn empty-processing?
  "Returns true if there are no pending tasks and nothing left to process."
  [{tasks :tasks process-ids :process-ids}]
  (and (empty? process-ids)
       (empty? tasks)))


;; FSM definition

(def default-job-value
  {:dataset-id     nil
   :user-ident     nil
   :collection-id  nil
   :type-field     nil
   :page           1
   :page-count     1
   :facility-count 0
   :process-count  0
   :facility-ids   []
   :process-ids    []
   :tasks          []
   :pending-tasks  []
   :next-task      nil
   :result         nil
   :last-event     nil
   :sites-without-location-count          0
   :sites-without-type-count              0
   :facilities-without-road-network-count 0
   :facilities-outside-regions-count      0})

(fsm/defsm-inc import-job
  [[:start
    [[_ :next]]                      -> {:action dispatch-import-types} :importing-types
    [[_ :cancel]]                    -> {:action cancel-import} :error
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:importing-types
    [[_ [:success :import-types _]]] -> {:action import-types-succeeded} :request-sites
    [[_ [:failure :import-types _]]] -> {:action import-types-failed} :error
    [[_ :next]]                      -> {:action clear-dispatch} :importing-types
    [[_ :cancel]]                    -> {:action cancel-import} :cancelling
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:request-sites
    [[_ :next]]                      -> {:action dispatch-import-sites} :importing-sites
    [[_ :cancel]]                    -> {:action cancel-import} :clean-up
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:importing-sites
    [_ :guard page-number-mismatch?] -> {:action unexpected-event} :error
    [[_ [:success [:import-sites _] [:continue _]]]]  -> {:action import-sites-succeeded} :request-sites
    [[_ [:success [:import-sites _] _]]]              -> {:action import-sites-succeeded} :delete-old-facilities
    [[_ [:failure [:import-sites _] _]]]              -> {:action import-sites-failed} :clean-up
    [[_ :next]]                      -> {:action clear-dispatch} :importing-sites
    [[_ :cancel]]                    -> {:action cancel-import} :cancelling
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:delete-old-facilities
    [[_ :next]]                      -> {:action dispatch-delete-old-facilities} :deleting-old-facilities
    [[_ :cancel]]                    -> {:action cancel-import} :clean-up
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:deleting-old-facilities
    [[_ :next]]                               -> {:action clear-dispatch} :deleting-old-facilities
    [[_ :cancel]]                             -> {:action cancel-import} :clean-up-wait
    [[_ [:success :delete-old-facilities _]]] -> {:action delete-old-facilities-succeeded} :delete-old-types
    [[_ [:failure :delete-old-facilities _]]] -> {:action delete-old-facilities-failed} :error
    [[_ _]]                                   -> {:action unexpected-event} :error]

   [:delete-old-types
    [[_ :next]]                      -> {:action dispatch-delete-old-types} :deleting-old-types
    [[_ :cancel]]                    -> {:action cancel-import} :clean-up
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:deleting-old-types
    [[_ :next]]                          -> {:action clear-dispatch} :deleting-old-types
    [[_ :cancel]]                        -> {:action cancel-import} :clean-up-wait
    [[_ [:success :delete-old-types _]]] -> {:action delete-old-types-succeeded} :update-projects
    [[_ [:failure :delete-old-types _]]] -> {:action delete-old-types-failed} :error
    [[_ _]]                              -> {:action unexpected-event} :error]

   [:update-projects
    [[_ :next]]                      -> {:action dispatch-update-projects} :updating-projects
    [[_ :cancel]]                    -> {:action cancel-import} :clean-up
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:updating-projects
    [[_ :next]]                      -> {:action clear-dispatch} :updating-projects
    [[_ :cancel]]                    -> {:action cancel-import} :clean-up-wait
    [[_ [:success :update-projects _]]] -> {:action update-projects-succeeded} :processing-facilities
    [[_ [:failure :update-projects _]]] -> {:action update-projects-failed} :error
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:processing-facilities
    [[(_ :guard empty-processing?) :next]] -> {:action nothing-to-process} :done
    [[_ :next]]                      -> {:action dispatch-process-facilities} :processing-facilities
    [[(_ :guard no-pending-tasks?) :cancel]] -> {:action cancel-import} :error
    [[_ :cancel]]                    -> {:action cancel-import} :clean-up-wait
    [_ :guard done-processing?]      -> {:action last-complete-processing} :done
    [[_ (_ :guard process-report?)]] -> {:action complete-processing} :processing-facilities
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:cancelling
    [_ :guard last-task-report?]     -> {:action complete-task} :clean-up
    [_ :guard event-task-report?]    -> {:action complete-task} :cancelling
    [[_ :cancel]]                    -> :cancelling
    [[_ :next]]                      -> {:action clear-dispatch} :cancelling]

   [:clean-up
    [[_ :next]]                      -> {:action dispatch-update-projects} :clean-up-wait
    [[_ :cancel]]                    -> :clean-up
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:clean-up-wait
    [_ :guard last-task-report?]     -> {:action complete-task} :error
    [_ :guard event-task-report?]    -> {:action complete-task} :clean-up-wait
    [[_ :cancel]]                    -> :clean-up-wait
    [[_ :next]]                      -> {:action clear-dispatch} :clean-up-wait]

   [:error   {:is-terminal true}]
   [:done    {:is-terminal true}]]

  :default-acc default-job-value

  :dispatch :event-acc-vec)

(defn job-peek-next-task
  [job]
  (get-in job [:value :next-task]))

(defn job-result
  [job]
  (get-in job [:value :result]))

(defn job-dataset-id
  [job]
  (get-in job [:value :dataset-id]))

(defn job-stats
  [job]
  (select-keys (:value job) [:sites-without-location-count
                             :sites-without-type-count
                             :facilities-without-road-network-count
                             :facilities-outside-regions-count]))

(defn job-user-ident
  [job]
  (get-in job [:value :user-ident]))

(defn job-collection-id
  [job]
  (get-in job [:value :collection-id]))

(defn job-type-field
  [job]
  (get-in job [:value :type-field]))

(defn job-facility-ids
  [job]
  (get-in job [:value :facility-ids]))

(defn job-finished?
  [job]
  (or (nil? job)
      (:is-terminated? job)))

(defn- import-progress
  [{:keys [page page-count]}]
  (when (some-> page-count pos?)
    (/ (dec page) page-count)))

(defn- process-progress
  [{:keys [process-ids process-count tasks]}]
  (when (some-> process-count pos?)
    (let [pending-ids (+ (count tasks) (count process-ids))]
      (- 1 (/ pending-ids process-count)))))

(defn job-status
  [job]
  (let [state (:state job)]
    (cond
      (nil? job)
      nil

      (nil? state)
      {:status :ready}

      (keyword? state)
      (case state
        (:cancelling :clean-up :clean-up-wait)
        {:status :cancelling
         :state state}

        (:error :done)
        {:status :done
         :state state
         :stats (job-stats job)}

        (:request-sites :importing-sites
         :delete-old-facilities :deleting-old-facilities
         :delete-old-types :deleting-old-types)
        {:status :importing
         :state state
         :progress (import-progress (:value job))}

        :processing-facilities
        {:status :importing
         :state state
         :progress (process-progress (:value job))}

        ;; else
        {:status :importing
         :state state
         :progress nil})

      true
      {:status :unknown})))

(defn create-job
  [dataset-id user-ident coll-id type-field]
  (let [job-value (assoc default-job-value
                         :dataset-id dataset-id
                         :user-ident user-ident
                         :collection-id coll-id
                         :type-field type-field)]
    (import-job job-value)))

(defn restore-job
  [{:keys [state value], :as job}]
  (import-job
    state
    (assoc value :pending-tasks (:tasks value))))

(defn serialize-job
  [job]
  (dissoc job :fsm))

(defn cancel-job
  [job]
  (when job
    (fsm/fsm-event job :cancel)))

(defn next-task
  [job]
  (when job
    (if-let [pending-tasks (seq (get-in job [:value :pending-tasks]))]
      (-> job
        (assoc-in [:value :next-task] (first pending-tasks))
        (update-in [:value :pending-tasks] rest))
      (fsm/fsm-event job :next))))

(defn report-task-success
  [job task-id data]
  (when job
    (fsm/fsm-event job [:success task-id data])))

(defn report-task-failure
  [job task-id error-info]
  (when job
    (fsm/fsm-event job [:failure task-id error-info])))
