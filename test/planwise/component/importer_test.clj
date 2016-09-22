(ns planwise.component.importer-test
  (:require [planwise.component.importer :as importer]
            [planwise.component.datasets :as datasets]
            [planwise.component.facilities :as facilities]
            [planwise.component.projects :as projects]
            [planwise.boundary.resmap :as resmap]
            [planwise.model.import-job :as import-job]
            [planwise.component.taskmaster :as taskmaster]
            [planwise.test-system :refer [test-system with-system]]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan <! >!] :as async]
            [clojure.java.jdbc :as jdbc]
            [planwise.test-utils :refer [make-point]]))

(def resmap-type-field
  {:name "Type",
   :code "type",
   :kind "select_one",
   :config {:options [{:id 1, :code "hospital", :label "Hospital"}
                      {:id 2, :code "health center", :label "Health Center"}
                      {:id 3, :code "dispensary", :label "Dispensary"}],
            :next_id 4}})

(def fixture-data
  [[:users
    [{:id 1 :email "john@doe.com" :full_name "John Doe" :last_login nil :created_at (time/ago (time/minutes 5))}]]
   [:datasets
    [{:id 1 :name "dataset1" :description "" :owner_id 1 :collection_id 1 :import_mappings nil}
     {:id 2 :name "dataset2" :description "" :owner_id 1 :collection_id 2 :import_mappings nil}]]])

(def fixture-data-with-facilities
  [[:users
    [{:id 1 :email "john@doe.com" :full_name "John Doe" :last_login nil :created_at (time/ago (time/minutes 5))}]]
   [:datasets
    [{:id 1 :name "dataset1" :description "" :owner_id 1 :collection_id 1 :import_mappings nil}
     {:id 2 :name "dataset2" :description "" :owner_id 1 :collection_id 2 :import_mappings nil}]]
   [:facility_types
    [{:id 1 :dataset_id 1 :name "Hospital"}
     {:id 2 :dataset_id 1 :name "Rural"}]]
   [:facilities
    [{:id 1 :dataset_id 1 :site_id 1 :name "Facility A" :type_id 1 :lat -3   :lon 42 :the_geom (make-point -3 42)   :processing_status "ok"}
     {:id 2 :dataset_id 1 :site_id 2 :name "Facility B" :type_id 1 :lat -3.5 :lon 42 :the_geom (make-point -3.5 42) :processing_status "ok"}
     {:id 3 :dataset_id 1 :site_id 3 :name "Facility C" :type_id 1 :lat -3.5 :lon 42 :the_geom (make-point -3.5 42) :processing_status "ok"}]]
   [:facilities_polygons
    [{:id 1 :facility_id 1 :threshold 900 :method "alpha-shape"}]]])


(def user-ident
  {:user-email "john@doe.com", :user-id 1})

(defn execute-sql
  [system sql]
  (jdbc/execute! (get-in system [:db :spec]) sql))

(defn mock-resmap [{sites :sites}]
  (reify resmap/Resmap
    (get-collection-sites
      [service user-ident coll-id {page :page}]
      {:sites (if (= 1 page) sites [])
       :totalPages (if sites 1 0)})

    (find-collection-field
      [service user-ident coll-id field-id]
      (assoc resmap-type-field :id field-id))))

(defn mock-taskmaster []
  (let [channel (chan)]
    (async/go-loop []
      (if-let [{response-channel :channel} (<! channel)]
        (>! response-channel [:ok nil]))
      (recur))
    channel))

(defn run-tasks
  [dispatcher]
  (loop []
    (when-let [{:keys [task-id task-fn]} (taskmaster/next-task dispatcher)]
      (let [result (task-fn)]
        (taskmaster/task-completed dispatcher task-id result)
        (recur)))))

(defn importer-service [system]
  (-> (importer/importer)
    (merge (select-keys system [:taskmaster :datasets :resmap :facilities :projects]))
    (component/start)))

(defn system [& [{sites :sites, data :data}]]
  (into
    (test-system {:fixtures {:data (or data fixture-data)}})
    {:taskmaster (mock-taskmaster)
     :resmap (mock-resmap {:sites sites})
     :datasets (component/using (datasets/datasets-store) [:db])
     :facilities (component/using (facilities/facilities-service {}) [:db])
     :projects  (component/using (projects/projects-service) [:db :facilities])}))


(deftest run-import
  (timbre/with-level :warn
    (with-system (system {:sites [{:id 1, :name "s1", :lat -1.2, :long 36.8,
                                   :properties {:type "hospital"}}]})
      (execute-sql system "ALTER SEQUENCE facilities_id_seq RESTART WITH 100")
      (execute-sql system "ALTER SEQUENCE facility_types_id_seq RESTART WITH 100")

      (let [importer (importer-service system)]
        (importer/run-import-for-dataset importer 1 user-ident)
        (run-tasks importer))

      (let [dataset (datasets/find-dataset (:datasets system) 1)]
        (is (= {:sites-without-location-count 0
                :sites-without-type-count 0
                :facilities-without-road-network-count 0
                :facilities-outside-regions-count 1
                :result :success}
               (:import-result dataset))))

      (let [types (facilities/list-types (:facilities system) 1)
            facilities (facilities/list-facilities (:facilities system) 1 {})
            [facility] facilities]
        (is (= (count types) 3))
        (is (= #{"Hospital" "Health Center" "Dispensary"} (set (map :name types))))
        (is (= (count facilities) 1))
        (is (= (select-keys facility [:id :name :lat :lon :type-id])
               {:id 100 :type-id 100 :name "s1" :lat -1.2M :lon 36.8M}))))))


(deftest run-reimport
  (timbre/with-level :warn
    (with-system (system {:data fixture-data-with-facilities
                          :sites [{:id 1, :name "Facility A2", :lat -3.0, :long 42.0, :properties {:type "dispensary"}}
                                  {:id 2, :name "Facility B2", :lat -1.2, :long 36.8, :properties {:type "hospital"}}
                                  {:id 4, :name "Facility D",  :lat -1.2, :long 36.8, :properties {:type "health center"}}]})

      (execute-sql system "ALTER SEQUENCE facilities_id_seq RESTART WITH 100")
      (execute-sql system "ALTER SEQUENCE facility_types_id_seq RESTART WITH 100")

      (let [importer (importer-service system)]
        (importer/run-import-for-dataset importer 1 user-ident)
        (run-tasks importer))

      (let [dataset (datasets/find-dataset (:datasets system) 1)]
        (is (= {:sites-without-location-count 0
                :sites-without-type-count 0
                :facilities-without-road-network-count 0
                :facilities-outside-regions-count 2
                :result :success}
               (:import-result dataset))))

      (let [types (facilities/list-types (:facilities system) 1)
            facilities (facilities/list-facilities (:facilities system) 1 {})]
        (is (= 3 (count types)))
        (is (= #{{:name "Hospital" :id 1}
                 {:name "Health Center" :id 100}
                 {:name "Dispensary" :id 101}}
               (set types)))

        (is (= 3 (count facilities)))
        (is (= #{{:id 1   :type-id 101 :name "Facility A2" :lat -3.0M :lon 42.0M :processing-status "ok"}               ; is updated but not reprocessed
                 {:id 2   :type-id 1   :name "Facility B2" :lat -1.2M :lon 36.8M :processing-status "outside-regions"}  ; is updated and reprocessed
                 {:id 100 :type-id 100 :name "Facility D"  :lat -1.2M :lon 36.8M :processing-status "outside-regions"}} ; is created
               (set (map #(select-keys % [:id :name :lat :lon :type-id :processing-status]) facilities))))))))


(deftest resume-importer-job
  (timbre/with-level :warn
    (with-system (system)
      (let [importer (importer-service system)]
        (let [[result statūs] (importer/run-import-for-dataset importer 1 user-ident)]))

      ; Start new import for a dataset
      (let [importer (importer-service system)]
        (let [[result statūs] (importer/run-import-for-dataset importer 1 user-ident)]
          (is (= :ok result))
          (is (= :importing (:status (get statūs 1)))))

        ; Proceed to next task in the import process
        (let [{task-id :task-id} (taskmaster/next-task importer)]
          (is (= [1 :import-types] task-id))
          (let [jobs (taskmaster/task-completed importer task-id :new-types)]
            (is (= :request-sites (:state (get jobs 1))))))

        (component/stop importer))

      ; Create a new importer component that should reload the jobs state from the previous one
      (let [importer (importer-service system)
            {task-id :task-id} (taskmaster/next-task importer)
            {next-id :task-id} (taskmaster/next-task importer)]
          (is (= [1 [:import-sites 1]] task-id))
          (is (nil? next-id))

        (component/stop importer)))))


(deftest resume-importer-job-with-multiple-tasks
  (timbre/with-level :warn
    (with-system (system)

      ; Create import job for a dataset with a processing-facilities state
      (let [importer (importer-service system)
            job (import-job/restore-job {:state :processing-facilities
                                         :value (merge import-job/default-job-value
                                                  {:page-count 1, :collection-id 1, :dataset-id 1,
                                                   :type-field {:code "type", :options {}},
                                                   :page 1, :user-ident user-ident,
                                                   :process-ids [1 2 3 4 5 6],
                                                   :process-count 6,
                                                   :facility-ids [1 2 3 4 5 6],
                                                   :facility-count 6})})]
        (swap! (:jobs importer) assoc 1 job)

        ; Fire 4 concurrent process facility tasks
        (let [tasks (repeatedly 4 #(taskmaster/next-task importer))
              expected-ids (for [i (range 4)] [1 [:process-facilities [(inc i)]]])
              actual-ids (map :task-id tasks)]
          (is (= expected-ids actual-ids))

          ; One of the tasks succeeds, so the intermediate job state is persisted
          (taskmaster/task-completed importer
                                     (:task-id (first tasks))
                                     [:success [:process-facilities [1]] nil])

          ; And then the importer dies
          (component/stop importer)))

      ; Create a new importer component that should re-run the pending tasks
      (let [importer (importer-service system)
            pending-tasks (get-in @(:jobs importer) [1 :value :pending-tasks])
            tasks (repeatedly 5 #(taskmaster/next-task importer))
            expected-ids (for [i (range 5)] [1 [:process-facilities [(+ 2 i)]]])
            actual-ids (map :task-id tasks)]

          ; Check that the tasks are restored with the job as pending-tasks
          (is (= 3 (count pending-tasks)))
          ; Check that the pending tasks are re-run, and then the following ones are dispatched
          (is (= expected-ids actual-ids))

        (component/stop importer)))))

(deftest facility-type-ctor-test
  (let [type-field {:code "facility_type"
                    :options {1 10,
                              2 11}}

        site       {:id 1, :name "s1",
                    :lat -1.28721692771827, :long 36.8136030697266,
                    :properties {:facility_type 1}}

        facility-type (importer/facility-type-ctor type-field)]

    (is (= 10 (facility-type site)))))


(deftest sites->facilities-test
  (let [type-field          {:code "facility_type",
                             :options {1 10,
                                       2 11}}

        sites               [{:id 1, :name "s1",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {:facility_type 1}}

                             ;; sites without location are ignored
                             {:id 2, :name "s2",
                              :properties {:facility_type 1}}

                             ;; sites without a type are ignored
                             {:id 3, :name "s3",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {}}
                             {:id 4, :name "s4",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {:facility_type nil}}]

        facility-type       (importer/facility-type-ctor type-field)
        facilities          (importer/sites->facilities sites facility-type)

        expected-facilities [{:lat -1.28721692771827,
                              :lon 36.8136030697266,
                              :name "s1",
                              :site-id 1,
                              :type-id 10}]]

    (is (= expected-facilities facilities))))
