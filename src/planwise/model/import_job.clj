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
  (if-not (= task (peek tasks))
    (conj tasks task)
    tasks))

(defn peek-task
  [tasks]
  (peek tasks))

(defn remove-task
  [tasks task]
  (->> tasks
       (remove #(= task %))
       vec))


;; FSM value update functions

(defn cancel-import
  [job & _]
  (assoc job :result :cancelled))

(defn unexpected-event
  [job evt state & _]
  (assoc job
         :result :unexpected-event
         :event evt
         :last-state state))

(defn clear-dispatch
  [job & _]
  (update job :tasks push-task nil))

(defn complete-task
  [job [_ task _] & _]
  (update job :tasks remove-task task))

(defn dispatch-import-types
  [job & _]
  (update job :tasks push-task :import-types))

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
    (update job :tasks push-task [:import-sites page])))

(defn import-sites-succeeded
  [job event & _]
  (let [page (:page job)
        facility-ids (:facility-ids job)
        [_ _ [_ page-ids]] event]
    (-> (complete-task job event)
        (assoc :page (inc page)
               :facility-ids (into facility-ids page-ids)))))

(defn import-sites-failed
  [job event & _]
  (let [[_ _ error] event]
    (-> (complete-task job event)
        (assoc :result :import-sites-failed
               :error-info error))))

(defn dispatch-process-facilities
  [job & _]
  (let [facility-ids (:facility-ids job)
        next-facility (first facility-ids)]
    (if (nil? next-facility)
      (clear-dispatch job)
      (-> job
          (update :tasks push-task [:process-facilities [next-facility]])
          (update :facility-ids rest)))))

(defn complete-processing
  [job event & _]
  ;; TODO: save the processing result in the job
  (complete-task job event))

(defn dispatch-update-projects
  [job & _]
  (update job :tasks push-task :update-projects))

(defn update-projects-succeeded
  [job event & _]
  (-> (complete-task job event)
      (assoc :result :success)))

(defn update-projects-failed
  [job event & _]
  (let [[_ _ error] event]
    (-> (complete-task job event)
        (assoc :result :update-projects-failed
               :error-info error))))


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
  (and (empty? (:facility-ids job))
       (process-report? event)
       (last-task-report? [job event])))


;; FSM definition

(def default-job-value
  {:user-ident    nil
   :collection-id nil
   :type-field    nil
   :page          1
   :facility-ids  []
   :tasks         []
   :result        nil
   :last-event    nil})

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
    [[_ [:success [:import-sites _] [:continue _]]]]
                                     -> {:action import-sites-succeeded} :request-sites
    [[_ [:success [:import-sites _] _]]]
                                     -> {:action import-sites-succeeded} :processing-facilities
    [[_ [:failure [:import-sites _] _]]]
                                     -> {:action import-sites-failed} :clean-up
    [[_ :next]]                      -> {:action clear-dispatch} :importing-sites
    [[_ :cancel]]                    -> {:action cancel-import} :cancelling
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:processing-facilities
    [[_ :next]]                      -> {:action dispatch-process-facilities} :processing-facilities
    [[(_ :guard no-pending-tasks?) :cancel]]
                                     -> {:action cancel-import} :clean-up
    [[_ :cancel]]                    -> {:action cancel-import} :cancelling
    [_ :guard done-processing?]      -> {:action complete-processing} :update-projects
    [[_ (_ :guard process-report?)]]       -> {:action complete-processing} :processing-facilities
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:update-projects
    [[_ :next]]                      -> {:action dispatch-update-projects} :updating-projects
    [[_ :cancel]]                    -> {:action cancel-import} :clean-up
    [[_ _]]                          -> {:action unexpected-event} :error]

   [:updating-projects
    [[_ :next]]                      -> {:action clear-dispatch} :updating-projects
    [[_ :cancel]]                    -> {:action cancel-import} :clean-up-wait
    [[_ [:success :update-projects _]]]
                                     -> {:action update-projects-succeeded} :done
    [[_ [:failure :update-projects _]]]
                                     -> {:action update-projects-failed} :error
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
  (peek-task (get-in job [:value :tasks])))

(defn job-result
  [job]
  (get-in job [:value :result]))

(defn job-user-ident
  [job]
  (get-in job [:value :user-ident]))

(defn job-collection-id
  [job]
  (get-in job [:value :collection-id]))

(defn job-type-field
  [job]
  (get-in job [:value :type-field]))

(defn job-finished?
  [job]
  (or (nil? job)
      (:is-terminated? job)))

(defn job-status
  [job]
  (let [state (:state job)]
    (cond
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
         :result (job-result job)}

        ;; else
        {:status :importing
         :state state
                                        ; TODO: calculate job progress
         :progress nil})

      true
      {:status :unknown})))

(defn create-job
  [user-ident coll-id type-field]
  (let [job-value (assoc default-job-value
                         :user-ident user-ident
                         :collection-id coll-id
                         :type-field type-field)]
    (import-job job-value)))

(defn cancel-job
  [job]
  (when job
    (fsm/fsm-event job :cancel)))

(defn next-task
  [job]
  (when job
    (fsm/fsm-event job :next)))

(defn report-task-success
  [job task-id data]
  (when job
    (fsm/fsm-event job [:success task-id data])))

(defn report-task-failure
  [job task-id error-info]
  (when job
    (fsm/fsm-event job [:failure task-id error-info])))
