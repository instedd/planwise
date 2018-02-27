(ns planwise.component.importer-test
  (:require [clojure.test :refer :all]
            [planwise.component.importer :as importer]
            [planwise.boundary.datasets :as datasets]
            [planwise.boundary.facilities :as facilities]
            [planwise.boundary.projects :as projects]
            [planwise.boundary.resmap :as resmap]
            [planwise.model.import-job :as import-job]
            [planwise.component.taskmaster :as taskmaster]
            [planwise.test-system :as test-system]
            [planwise.test-utils :refer [make-point sample-polygon]]
            [clj-time.core :as time]
            [clj-time.jdbc]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan <! >!] :as async]
            [clojure.java.jdbc :as jdbc]
            [integrant.core :as ig]))

(def resmap-type-field
  {:name "Type",
   :code "type",
   :kind "select_one",
   :config {:options [{:id 1, :code "hospital", :label "General Hospital"}
                      {:id 2, :code "health", :label "Health Center"}
                      {:id 3, :code "dispensary", :label "Dispensary"}],
            :next_id 4}
   :metadata [{:key "hospital-capacity", :value "500"}
              {:key "invalid-key", :value "200"}
              {:key "dispensary-capacity", :value "300"}
              {:key "health-capacity", :value "INVALID"}]})

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
   [:regions
    [{:id 1 :country "kenya" :name "Kenya" :admin_level 2 :the_geom (sample-polygon :large) :preview_geom nil :total_population 1000 :max_population 127 :raster_pixel_area 950}]]
   [:projects
    [{:id 1 :goal "project1" :region_id 1 :owner_id 1 :dataset_id 1
      :filters "{:facilities {:type [1 2]}}}"}]]
   [:facility_types
    [{:id 1 :dataset_id 1 :name "Hospital" :code "hospital"}
     {:id 2 :dataset_id 1 :name "Rural" :code "rural"}]]
   [:facilities
    [{:id 1 :dataset_id 1 :site_id 1 :name "Facility A" :type_id 1 :lat -3   :lon 42 :the_geom (make-point -3 42)   :processing_status "not-change"}
     {:id 2 :dataset_id 1 :site_id 2 :name "Facility B" :type_id 1 :lat -3.5 :lon 42 :the_geom (make-point -3.5 42) :processing_status "ok"}
     {:id 3 :dataset_id 1 :site_id 3 :name "Facility C" :type_id 1 :lat -3.5 :lon 42 :the_geom (make-point -3.5 42) :processing_status "ok"}]]
   [:facilities_polygons
    [{:id 1 :facility_id 1 :threshold 900 :method "alpha-shape"}]]])


(def user-ident
  {:user-email "john@doe.com", :user-id 1})

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
 ([dispatcher]
  (run-tasks dispatcher nil))
 ([dispatcher until-state]
  (loop []
    (when-let [{:keys [task-id task-fn]} (taskmaster/next-task dispatcher)]
      (let [result (task-fn)
            jobs (taskmaster/task-completed dispatcher task-id result)]
        (when-not (and until-state (= until-state (:state (get jobs 1))))
          (recur)))))))


(defmethod ig/init-key :planwise.test/resmap
  [_ config]
  (mock-resmap config))

(defn test-config
  [& [{sites :sites, data :data}]]
  (test-system/config
   {:planwise.test/fixtures        {:fixtures (or data fixture-data)}
    :planwise.test/resmap          {:sites sites}
    :planwise.component/datasets   {:db (ig/ref :duct.database/sql)}
    :planwise.component/facilities {:db (ig/ref :duct.database/sql)}
    :planwise.component/projects   {:db (ig/ref :duct.database/sql)
                                    :facilities (ig/ref :planwise.component/facilities)}}))

(defn importer-service
  [system]
  (ig/init-key :planwise.component/importer
               {:taskmaster          (mock-taskmaster)
                :resmap              (:planwise.test/resmap system)
                :facilities-capacity 100
                :datasets            (:planwise.component/datasets system)
                :facilities          (:planwise.component/facilities system)
                :projects            (:planwise.component/projects system)}))

(deftest run-import
  (timbre/with-level :warn
    (test-system/with-system (test-config {:sites [{:id 1, :name "s1", :lat -1.2, :long 36.8,
                                                    :properties {:type "hospital"}}]})
      (test-system/execute-sql system "ALTER SEQUENCE facilities_id_seq RESTART WITH 100")
      (test-system/execute-sql system "ALTER SEQUENCE facility_types_id_seq RESTART WITH 100")

      (let [importer (importer-service system)]
        (importer/run-import-for-dataset importer 1 user-ident)
        (run-tasks importer))

      (let [dataset (datasets/find-dataset (:planwise.component/datasets system) 1)]
        (is (= {:sites-without-location-count 0
                :sites-without-type-count 0
                :facilities-without-road-network-count 0
                :facilities-outside-regions-count 1
                :result :success}
               (:import-result dataset))))

      (let [types (facilities/list-types (:planwise.component/facilities system) 1)
            facilities (facilities/list-facilities (:planwise.component/facilities system) 1 {})
            [facility] facilities]
        (is (= 3 (count types)))
        (is (= (set (map :name types)) #{"General Hospital" "Health Center" "Dispensary"}))
        (is (= 1 (count facilities)))
        (is (= (select-keys facility [:id :name :lat :lon :type-id :capacity])
               {:id 100 :type-id 100 :name "s1" :lat -1.2M :lon 36.8M :capacity 500}))))))

(deftest run-reimport
  (timbre/with-level :warn
    (test-system/with-system (test-config {:data fixture-data-with-facilities
                                           :sites [{:id 1, :name "Facility A2", :lat -3.0, :long 42.0, :properties {:type "dispensary"}}
                                                   {:id 2, :name "Facility B2", :lat -1.2, :long 36.8, :properties {:type "hospital"}}
                                                   {:id 4, :name "Facility D",  :lat -1.2, :long 36.8, :properties {:type "health"}}]})

      (test-system/execute-sql system "ALTER SEQUENCE facilities_id_seq RESTART WITH 100")
      (test-system/execute-sql system "ALTER SEQUENCE facility_types_id_seq RESTART WITH 100")

      (let [importer (importer-service system)]
        (importer/run-import-for-dataset importer 1 user-ident)
        (run-tasks importer))

      (let [dataset (datasets/find-dataset (:planwise.component/datasets system) 1)]
        (is (= {:sites-without-location-count 0
                :sites-without-type-count 0
                :facilities-without-road-network-count 0
                :facilities-outside-regions-count 0
                :result :success}
               (:import-result dataset))))

      (let [project (projects/get-project (:planwise.component/projects system) 1)]
        (is (= {:facilities-targeted 1, :facilities-total 3}
               (:stats project))))

      (let [types (facilities/list-types (:planwise.component/facilities system) 1)
            facilities (facilities/list-facilities (:planwise.component/facilities system) 1 {})]
        (is (= 3 (count types)))
        (is (= #{{:code "hospital"   :name "General Hospital" :id 1} ; Type label should be updated, even if id does not change
                 {:code "health"     :name "Health Center" :id 100}
                 {:code "dispensary" :name "Dispensary" :id 101}}
               (set types)))

        (is (= 3 (count facilities)))
        (is (= #{{:id 1   :type-id 101 :name "Facility A2" :lat -3.0M :lon 42.0M :capacity 300 :processing-status "not-change"} ; is updated but not reprocessed
                 {:id 2   :type-id 1   :name "Facility B2" :lat -1.2M :lon 36.8M :capacity 500 :processing-status "ok"} ; is updated and reprocessed
                 {:id 100 :type-id 100 :name "Facility D"  :lat -1.2M :lon 36.8M :capacity 100 :processing-status "ok"}} ; is created
               (set (map #(select-keys % [:id :name :lat :lon :type-id :processing-status :capacity]) facilities))))))))


(deftest resume-importer-job
  (timbre/with-level :warn
    (test-system/with-system (test-config)

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

        (ig/halt-key! :planwise.component/importer importer))

      ; Create a new importer component that should reload the jobs state from the previous one
      (let [importer (importer-service system)
            {task-id :task-id} (taskmaster/next-task importer)
            {next-id :task-id} (taskmaster/next-task importer)]
        (is (= [1 [:import-sites 1]] task-id))
        (is (nil? next-id))

        (ig/halt-key! :planwise.component/importer importer)))))


(deftest resume-importer-job-with-multiple-tasks
  (timbre/with-level :warn
    (test-system/with-system (test-config)

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
          (ig/halt-key! :planwise.component/importer importer)))

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

          (ig/halt-key! :planwise.component/importer importer)))))

(deftest update-cancelled-dataset
  (timbre/with-level :warn
    (test-system/with-system (test-config {:sites [{:id 1, :name "s1", :lat -1.2, :long 36.8,
                                                    :properties {:type "hospital"}}]})
      (test-system/execute-sql system "ALTER SEQUENCE facilities_id_seq RESTART WITH 100")
      (test-system/execute-sql system "ALTER SEQUENCE facility_types_id_seq RESTART WITH 100")

      (let [importer (importer-service system)]

        ; Start new import for a dataset, run until delete-old-types, and cancel it
        (importer/run-import-for-dataset importer 1 user-ident)
        (run-tasks importer :delete-old-types)
        (importer/cancel-import! importer 1)
        (run-tasks importer)

        ; Check that the job was finished and a site was imported, though not processed
        (is empty? @(:jobs importer))
        (let [dataset (datasets/find-dataset (:planwise.component/datasets system) 1)]
          (is (= :cancelled (get-in dataset [:import-result :result]))))
        (let [[facility] (facilities/list-facilities (:planwise.component/facilities system) 1 {})]
          (is (= {:id 100, :site-id 1, :processing-status nil}
                 (select-keys facility [:id :site-id :processing-status]))))

        ; Run a new import for the dataset
        (importer/run-import-for-dataset importer 1 user-ident)
        (run-tasks importer)

        ; Check that the job was finished and the site was processed
        (is empty? @(:jobs importer))
        (let [dataset (datasets/find-dataset (:planwise.component/datasets system) 1)]
          (is (= :success (get-in dataset [:import-result :result]))))
        (let [[facility] (facilities/list-facilities (:planwise.component/facilities system) 1 {})]
          (is (= {:id 100, :site-id 1, :processing-status "outside-regions"}
                 (select-keys facility [:id :site-id :processing-status]))))

        (ig/halt-key! :planwise.component/importer importer)))))

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
                                       2 11}
                             :capacities {1 500}}

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
        facility-capacity   (importer/facility-capacity-ctor type-field 800)
        facilities          (importer/sites->facilities sites facility-type facility-capacity)

        expected-facilities [{:lat -1.28721692771827,
                              :lon 36.8136030697266,
                              :name "s1",
                              :site-id 1,
                              :type-id 10
                              :capacity 500}]]

    (is (= expected-facilities facilities))))
