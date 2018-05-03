(ns planwise.component.jobrunner
  (:require [planwise.boundary.jobrunner :as boundary]
            [integrant.core :as ig]
            [planwise.component.taskmaster :as taskmaster]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn- try-job-next-task
  [job state]
  (try
    (boundary/job-next-task job state)
    (catch Throwable e
      (error (str "Exception fetching next task from job "
                  (pr-str job) " with state " (pr-str state)))
      {:state nil})))

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
          result    (try-job-next-task job state)
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

(extend-protocol boundary/JobRunner
  JobRunner
  (queue-job [runner job job-state]
    (queue-job runner job job-state)))

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
