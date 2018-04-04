(ns planwise.component.jobrunner
  (:require [integrant.core :as ig]
            [planwise.component.taskmaster :as taskmaster]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

;; Job types are identified by a keyword, preferrably namespaced (eg.
;; :planwise.jobs/dataset-process)

;; Job identifiers can consist of a job type alone, or vector whose first
;; element is the job type (eg. [:planwise.jobs/dataset-process 42])

;; Jobs also have an arbitrary value representing the state. If nil, the job is
;; considered finished and will be removed from the executing component.

(defn job-type
  [job]
  (if (vector? job) (first job) job))

;;;
;;; Jobs interface
;;;

(defmulti job-next-task
  "Retrieve the next task to execute for this job. Must return a map with a
  :state key containing the next state, :task-id with an internal task
  identifier and :task-fn with the task implementation itself."
  (fn [job state] (job-type job)))

(defmethod job-next-task :default
  [job state]
  ;; no more tasks to execute, job is finished
  nil)

(defmulti job-cancel!
  "Requests a job cancellation for a running job. Returns the updated state."
  (fn [job state] (job-type job)))

(defmethod job-cancel! :default
  [job state]
  ;; ignored by default
  state)

(defmulti job-task-success
  "Receive the report of a successfully completed task for the job. Returns the
  updated state."
  (fn [job state task result] (job-type job)))

(defmethod job-task-success :default
  [job state task result]
  ;; ignore by default
  state)

(defmulti job-task-failure
  "Receive the report of a failed task for the job. Returns the updated state."
  (fn [job state task error-info] (job-type job)))

(defmethod job-task-failure :default
  [job state task error-info]
  ;; ignore by default
  state)


(defn fetch-next-task
  "Recursively polls jobs taken from queue in order for a next task, updating
  job states and accumulating idle jobs, until a job returns a next task to
  execute. Returns the updated job states, queue and found task with finished
  jobs removed from the queue and the job states. If queue is empty, the job
  states and the queue constructed from the idles queue."
  [{:keys [queue idles jobs]}]
  (if (empty? queue)
    {:queue (vec idles) :jobs jobs}
    (let [job       (first queue)
          queue'    (vec (rest queue))
          state     (get jobs job)
          result    (job-next-task job state)
          state'    (:state result)
          next-task (when-let [task-id (:task-id result)]
                      {:task-id [job task-id]
                       :task-fn (:task-fn result)})
          jobs'     (if (some? state') (assoc jobs job state') (dissoc jobs job))
          idles'    (if (some? state') (conj (vec idles) job) idles)]
      (if (some? next-task)
        {:queue (into queue' idles')
         :jobs jobs'
         :next-task next-task}
        (recur {:queue queue'
                :idles idles'
                :jobs jobs'})))))

(defn insert-job
  "Inserts the job with an initial state and adds it to the tail of the queue.
  Does nothing if the job was already there."
  [{:keys [queue jobs] :as state} job job-state]
  (if (contains? jobs job)
    state
    (assoc state
           :jobs (assoc jobs job job-state)
           :queue (conj (vec queue) job))))

(defrecord JobRunner [taskmaster state]
  taskmaster/TaskDispatcher
  (next-task [{:keys [state]}]
    (let [state' (swap! state fetch-next-task)]
      (:next-task state')))
  (task-completed [this task-id result]
    ;; TODO
    nil)
  (task-failed [this task-id error-info]
    ;; TODO
    nil))

(defn queue-job
  ([runner job]
   (queue-job runner job {}))
  ([{:keys [taskmaster state]} job job-state]
   (let [state'     (swap! state insert-job job job-state)
         queue'     (:queue state')
         jobs'      (:jobs state')
         enqueued?  (some #{job} queue')
         job-state' (get jobs' job)]
     (taskmaster/poll-dispatcher taskmaster)
     (cond
       (and enqueued? (= job-state job-state')) :enqueued
       enqueued? :duplicate
       :else :failed))))

(defn cancel-job!
  [runner job]
  ;; TODO
  nil)

(defn status
  [runner]
  ;; TODO
  nil)

(defmethod ig/init-key :planwise.component/jobrunner
  [_ {:keys [concurrent-workers]}]
  (info "Starting job runner")
  (let [runner (map->JobRunner {:state (atom {:jobs {} :queue []})})]
    (assoc runner :taskmaster (taskmaster/run-taskmaster runner (or concurrent-workers 1)))))

(defmethod ig/halt-key! :planwise.component/jobrunner
  [_ runner]
  (info "Stopping job runner")
  (when-let [taskmaster (:taskmaster runner)]
    (taskmaster/quit taskmaster)))
