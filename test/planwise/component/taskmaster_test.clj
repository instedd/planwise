(ns planwise.component.taskmaster-test
  (:require [planwise.component.taskmaster :as sut]
            [clojure.test :as t]))

;; Dummy test implementation of TaskDispatcher

(def next-task-id (atom 0))
(def tasks (atom []))

(defn pop-task
  []
  (let [current-tasks @tasks
        task (peek current-tasks)
        updated-tasks (if (empty? current-tasks)
                        current-tasks
                        (pop current-tasks))]
    (if (compare-and-set! tasks current-tasks updated-tasks)
      task
      (recur))))

(defn queue-task
  ([task-fn]
   (queue-task (fn [_] (task-fn)) nil))
  ([task-fn task-data]
   (let [task-id (swap! next-task-id inc)
         task {:task-id task-id
               :work-fn task-fn
               :work-data task-data}]
     (swap! tasks #(vec (cons task %)))
     task-id)))

(def dummy-task-dispatcher
  (reify sut/TaskDispatcher
    (next-task [this]
      (pop-task))
    (task-completed [this task-id result]
      (println (str "Task #" task-id " completed successfully with result " result)))
    (task-failed [this task-id error-info]
      (println (str "Task #" task-id " failed with " error-info)))))
