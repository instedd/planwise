(ns planwise.component.jobrunner-test
  (:require [clojure.test :refer :all]
            [planwise.component.jobrunner :as sut]))


(defmethod sut/job-next-task ::idler
  [_ state]
  {:state state})

(defmethod sut/job-next-task ::finished
  [_ _]
  nil)

(defmethod sut/job-next-task ::busy
  [_ value]
  {:state (inc value)
   :task-id (inc value)
   :task-fn (constantly :busy)})

(deftest fetch-next-task-test
  (let [state {:jobs {::idler    :idle
                      ::finished :foo
                      ::busy     0}}]

    ;; with an empty queue, nothing should happen
    (let [state' (sut/fetch-next-task (assoc state :queue []))]
      (is (nil? (:next-task state')))
      (is (empty? (:queue state')))
      (is (= (:jobs state) (:jobs state'))))

    ;; with only an idler, nothing should happen either, but the job should
    ;; remain in the queue
    (let [state' (sut/fetch-next-task (assoc state :queue [::idler]))]
      (is (nil? (:next-task state')))
      (is (= [::idler] (:queue state')))
      (is (= (:jobs state) (:jobs state'))))

    ;; a finished job should be removed from the queue and the state map
    (let [state' (sut/fetch-next-task (assoc state :queue [::finished]))]
      (is (nil? (:next-task state')))
      (is (empty? (:queue state')))
      (is (not (contains? (:jobs state') ::finished))))

    ;; a busy job returns a task, updates the state and remains in the queue
    (let [state' (sut/fetch-next-task (assoc state :queue [::busy]))]
      (is (some? (:next-task state')))
      (is (= [::busy 1] (get-in state' [:next-task :task-id])))
      (is (= :busy ((get-in state' [:next-task :task-fn]))))
      (is (= [::busy] (:queue state')))
      (is (= 1 (get-in state' [:jobs ::busy]))))

    ;; longer queues are processed
    (let [state' (sut/fetch-next-task (assoc state :queue [::idler ::finished ::busy]))]
      (is (= [::idler ::busy] (:queue state')))
      (is (= [::busy 1] (get-in state' [:next-task :task-id]))))
    (let [state' (sut/fetch-next-task (assoc state :queue [::idler ::busy ::finished]))]
      (is (= [::finished ::idler ::busy] (:queue state')))
      (is (= [::busy 1] (get-in state' [:next-task :task-id]))))
    (let [state' (sut/fetch-next-task (assoc state :queue [::finished ::idler ::busy]))]
      (is (= [::idler ::busy] (:queue state')))
      (is (= [::busy 1] (get-in state' [:next-task :task-id]))))
    (let [state' (sut/fetch-next-task (assoc state :queue [::finished ::busy ::idler]))]
      (is (= [::idler ::busy] (:queue state')))
      (is (= [::busy 1] (get-in state' [:next-task :task-id]))))
    (let [state' (sut/fetch-next-task (assoc state :queue [::busy ::idler ::finished]))]
      (is (= [::idler ::finished ::busy] (:queue state')))
      (is (= [::busy 1] (get-in state' [:next-task :task-id]))))
    (let [state' (sut/fetch-next-task (assoc state :queue [::busy ::finished ::idler]))]
      (is (= [::finished ::idler ::busy] (:queue state')))
      (is (= [::busy 1] (get-in state' [:next-task :task-id])))))

  ;; vector job identifiers are supported correctly
  (let [state {:jobs {[::busy :a] 100
                      [::busy :b] 200}}]
    (let [state' (sut/fetch-next-task (assoc state :queue [[::busy :a] [::busy :b]]))]
      (is (= {[::busy :a] 101 [::busy :b] 200} (:jobs state')))
      (is (= [[::busy :b] [::busy :a]] (:queue state')))
      (is (= [[::busy :a] 101] (get-in state' [:next-task :task-id]))))
    (let [state' (sut/fetch-next-task (assoc state :queue [[::busy :b] [::busy :a]]))]
      (is (= {[::busy :a] 100 [::busy :b] 201} (:jobs state')))
      (is (= [[::busy :a] [::busy :b]] (:queue state')))
      (is (= [[::busy :b] 201] (get-in state' [:next-task :task-id]))))))
