(ns planwise.boundary.jobrunner)

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
  identifier and :task-fn with the task implementation itself. A nil state
  signals a finished job."
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


(defprotocol JobRunner
  (queue-job [runner job job-state]
    "Queue a new job"))
