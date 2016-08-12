(ns planwise.component.taskmaster
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [clojure.core.async :refer [chan close! put! <! >! go
                                        alt!! alts! alts!! timeout]]))

(timbre/refer-timbre)

(def Task
  {:task-id s/Any
   :task-fn (s/pred fn?)})

(defprotocol TaskDispatcher
  (next-task [this]
    "Retrieve the next available task, or nil if there is none.")
  (task-completed [this task-id result]
    "Report back the successful execution of the task identified by task-id.
    result is the task value.")
  (task-failed [this task-id error-info]
    "Report back the failed execution of the task identified by task-id.
    error-info contains the task exception information."))

(declare process-worker-msg)

(defn wait-workers
  [{:keys [workers] :as state}]
  (when (pos? (count workers))
    (info (str "Waiting 10s for " (count workers) " workers"))
    (let [wait-timeout (timeout 10000)]
      (loop [state state]
        (let [workers (:workers state)
              channels (conj workers wait-timeout)
              [msg channel-msg] (alts!! channels)]
          (if (= wait-timeout channel-msg)
            (do
              (warn (str "Timeout waiting for workers to finish. "
                         (count workers) " didn't finish")))
            (let [new-state (process-worker-msg state channel-msg msg)]
              (when (seq (:workers new-state))
                (recur new-state))))))))
  nil)

(defn dispatch-task
  [{:keys [task-id task-fn]}]
  (go
    (try
      (let [result (task-fn)]
        {:task-id task-id
         :status :ok
         :data result})
      (catch Exception e
        {:task-id task-id
         :status :error
         :error e}))))

(defn try-spawn-worker
  [dispatcher]
  (debug "Polling task dispatcher...")
  (when-let [task (and dispatcher (next-task dispatcher))]
    (dispatch-task task)))

(defn can-spawn-worker?
  [{:keys [workers scale]}]
  (> scale (count workers)))

(defn execute-control-msg
  [state {:keys [command] :as msg}]
  (if (nil? msg)
    (do
      (warn "Control channel closed! Aborting Taskmaster...")
      (wait-workers state))

    (case command
      :quit
      (let [new-state (wait-workers state)]
        (info "Taskmaster finished")
        [new-state :finished])

      :ping
      [state :pong]

      :poll-dispatcher
      ;; dispatcher will be polled on the next event loop iteration
      [state :ok]

      ;; else
      (do
        (warn (str "Got unknown command " (dissoc msg :channel)))
        [state :unknown-command]))))

(defn process-control-msg
  [state msg]
  (debug "Got control message:" msg)
  (let [channel (:channel msg)
        [new-state reply] (execute-control-msg state msg)]
    (when (and channel (some? reply))
      (put! channel reply))
    new-state))

(defn process-worker-msg
  [state worker-channel msg]
  (debug "Got worker message:" msg)
  (let [dispatcher (:dispatcher state)
        task-id (:task-id msg)
        task-status (:status msg)]
    (case task-status
      :ok
      (task-completed dispatcher task-id (:data msg))
      :error
      (task-failed dispatcher task-id (:error msg))))
  (update-in state [:workers] #(filterv (partial not= worker-channel) %)))

(defn spawn-workers
  [state]
  (if-let [new-worker (and (can-spawn-worker? state)
                           (try-spawn-worker (:dispatcher state)))]
    (recur (update-in state [:workers] #(conj % new-worker)))
    state))

(defn run-taskmaster
  ([dispatcher]
   (run-taskmaster dispatcher 1))
  ([dispatcher scale]
   (let [control-channel (chan)]
     (go
       (info "Taskmaster started")
       (try
         (loop [state {:dispatcher dispatcher
                       :scale scale
                       :workers []}]
           (let [state (spawn-workers state)
                 worker-channels (vec (:workers state))
                 channels (conj worker-channels control-channel)
                 [msg channel-msg] (alts! channels)
                 source (if (= control-channel channel-msg)
                          :control
                          :worker)]
             (case source
               :control
               (let [new-state (process-control-msg state msg)]
                 (when (some? new-state)
                   (recur new-state)))

               :worker
               (let [new-state (process-worker-msg state channel-msg msg)]
                 (when (some? new-state)
                   (recur new-state))))))

         (catch Exception e
           (warn "Exception in Taskmaster" e))

         (finally
           (close! control-channel))))

     control-channel)))


(defn send-command
  ([control-channel command]
   (send-command control-channel command 5000))
  ([control-channel command wait]
   (let [command (if (map? command) command {:command command})
         response-channel (chan)]
     (if (put! control-channel (assoc command :channel response-channel))
       (alt!!
         response-channel ([val _ch] [:ok val])
         (timeout wait) [:error :timeout])
       [:error :closed]))))


(defn ping
  [control-channel]
  (send-command control-channel :ping))

(defn quit
  [control-channel]
  ;; Wait 1s more than the wait timeout
  (send-command control-channel :quit 11000))

(defn poll-dispatcher
  [control-channel]
  (send-command control-channel :poll-dispatcher))
