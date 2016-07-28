(ns planwise.component.importer
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :refer [chan put! poll! offer! <! >! go go-loop]]
            [planwise.component.resmap :as resmap]
            [planwise.component.projects :as projects]
            [planwise.component.facilities :as facilities]))

(timbre/refer-timbre)

;; Field definition from resmap service
;; {:id "15890", :name "Type", :code "type", :kind "select_one", :config {:options [{:id 1, :code "hospital", :label "Hospital"} {:id 2, :code "health center", :label "Health Center"} {:id 3, :code "dispensary", :label "Dispensary"}], :next_id 4}}

(defn import-cancelled?
  [{:keys [control-channel status]}]
  (if (and (seq? @status) (= (first @status) :cancelling))
    true
    (when-let [msg (poll! control-channel)]
      (cond
        (= :cancel (first msg))
        (do
          (info "Cancelling import process upon request")
          (swap! status (constantly :cancelling))
          true)

        (= :quit (first msg))
        (do
          (info "Cancelling import process (service is quitting)")
          (swap! status (constantly :cancelling))
          (offer! control-channel msg)
          true)

        true
        (warn "Ignored message while importing: " msg)))))

(defn import-types
  [facilities type-field]
  (info "Importing facility types from Resourcemap field" (:id type-field))
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

(defn sites-with-location [sites]
  (filter #(and (:lat %) (:long %)) sites))

(defn facility-type-ctor [type-field]
  (let [field-name (:code type-field)
        options (:options type-field)
        type-path [:properties (keyword field-name)]]
    (fn [site]
      (let [f_type (get-in site type-path)]
        (get options f_type)))))

(defn site->facility-ctor [type-field]
  (let [facility-type (facility-type-ctor type-field)]
    (fn [site]
      (-> site
          (select-keys [:id :name :lat :long])
          (rename-keys {:long :lon})
          (assoc :type_id (facility-type site))))))

(defn sites->facilities [sites type-field]
  (->> sites
       (sites-with-location)
       (map (site->facility-ctor type-field))))

(defn do-import-sites
  [{:keys [resmap facilities] :as service} user coll-id type-field]
  (loop [page 1
         ids []]
    (when (import-cancelled? service)
      (throw (RuntimeException. "Import cancelled while importing sites")))
    (let [data (resmap/get-collection-sites resmap user coll-id {:page page})
          sites (:sites data)]
      (if (seq sites)
        (do (info "Processing page" page "of collection" coll-id)
            (let [new-facilities (sites->facilities sites type-field)]
              (info "Inserting" (count new-facilities) "facilities")
              (facilities/insert-facilities! facilities new-facilities)
              (recur (inc page) (into ids (map :id new-facilities)))))
        ids))))

(defn preprocess-isochrones
  [{:keys [facilities status] :as service} ids]
  (let [total-facilities (count ids)]
    (info "Preprocessing isochrones for" total-facilities "facilities")
    (doseq [[idx facility-id] (map-indexed vector ids)]
      (when (import-cancelled? service)
        (throw (RuntimeException. "Import cancelled while processing isochrones")))
      (let [progress (str (inc idx) "/" total-facilities)]
        (info "Preprocessing facility" facility-id progress)
        (swap! status (constantly [:importing :processing progress])))
      (facilities/preprocess-isochrones facilities facility-id))))

(defn update-projects
  [{:keys [projects status] :as service}]
  (info "Updating projects stats")
  (swap! status (constantly [:importing :updating]))
  (let [list (projects/list-projects projects)]
    (doseq [project list]
      (projects/update-project-stats projects project))))

(defn do-import-collection
  [{:keys [resmap facilities] :as service} user coll-id type-field]
  (info "Destroying existing facilities")
  (facilities/destroy-facilities! facilities)
  (try
    (let [type-field (import-types facilities type-field)
          ids (do-import-sites service user coll-id type-field)]
      (preprocess-isochrones service ids)
      (info "Done importing facilities from collection" coll-id))

    (finally
      (update-projects service))))

(defn service-loop
  [service]
  (info "Starting importer service")
  (let [c (:control-channel service)
        status (:status service)]
    (go-loop []
      (let [msg (<! c)]
        (info "Received message" msg)
        (if-not (= (first msg) :quit)
          (do
            (cond
              (= (first msg) :set-status)
              (swap! status (constantly (second msg)))

              (= (first msg) :import!)
              (let [params (second msg)
                    ident (:user params)
                    coll-id (:coll-id params)
                    type-field (:type-field params)]
                (swap! status (constantly [:importing "Importing collection"]))
                (try
                  (do-import-collection service ident coll-id type-field)
                  (swap! status (constantly [:ready :success]))
                  (catch Exception e
                    (error e "Error running import job")
                    (swap! status (constantly [:ready :failed])))))

              true
              (warn "Unknown message received" msg))
            (recur))
          (info "Finishing importer service")))))
  service)

(defrecord Importer [status control-channel resmap facilities projects]
  component/Lifecycle
  (start [component]
    (info "Starting Importer component")
    (if-not (:status component)
      (let [c (chan)]
        (-> component
            (assoc :status (atom :ready)
                   :control-channel c)
            (service-loop)))
      component))
  (stop [component]
    (info "Stopping Importer component")
    (when-let [c (:control-channel component)]
      (put! c [:quit]))
    (dissoc component :status :control-channel)))

(defn importer
  ([]
   (importer {}))
  ([config]
   (map->Importer config)))

(defn status
  [service]
  @(:status service))

(defn send-msg
  [service msg]
  (put! (:control-channel service) msg))

(defn import-collection
  [service user coll-id type-field]
  ;; TODO: this shouldn't be here, but otherwise this function returns before
  ;; the job is processed and the status is still :ready
  (swap! (:status service) (constantly [:importing "Starting..."]))
  (send-msg service [:import! {:user user
                               :coll-id coll-id
                               :type-field type-field}]))

(defn cancel-import!
  [service]
  (send-msg service [:cancel]))
