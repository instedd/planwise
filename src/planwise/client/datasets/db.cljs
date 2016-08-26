(ns planwise.client.datasets.db
  (:require [schema.core :as s :include-macros true]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [format-percentage]]))

(s/defschema ServerStatus
  {:status                    (s/enum :ready :done :importing :cancelling :unknown)
   (s/optional-key :state)    s/Keyword
   (s/optional-key :progress) s/Any})

(s/defschema Dataset
  "Information pertaining a single dataset"
  {:id                             s/Int
   :name                           s/Str
   :description                    s/Str
   :collection-id                  s/Int
   :mappings                       s/Any
   :facility-count                 s/Int
   :project-count                  s/Int
   (s/optional-key :state)         (s/enum :ready
                                           :import-requested
                                           :importing
                                           :cancel-requested
                                           :cancelling)
   (s/optional-key :server-status) (s/maybe ServerStatus)})

(s/defschema ViewState
  (s/enum nil
          :list
          :create-dialog
          :creating))

(s/defschema ResourcemapData
  {:authorised? s/Bool
   :collections s/Any})

(s/defschema DatasetsViewModel
  "Datasets related portion of the client database"
  {:state            ViewState
   :list             (asdf/Asdf (s/maybe [Dataset]))
   :last-refresh     s/Num
   :search-string    s/Str
   :resourcemap      (asdf/Asdf (s/maybe ResourcemapData))
   :new-dataset-data (s/maybe {(s/optional-key :collection) s/Any
                               (s/optional-key :type-field) s/Any})})

(def initial-db
  {:state            nil
   :list             (asdf/new nil)       ; List of available datasets
   :last-refresh     0
   :search-string    ""                   ; Dataset search string

   :resourcemap      (asdf/new nil)

   :new-dataset-data nil})


(defn show-dialog?
  [view-state]
  (or (= :create-dialog view-state)
      (= :creating view-state)))

(defn dataset-importing?
  [state]
  (or (= :importing state)
      (= :import-requested state)
      (= :cancelling state)
      (= :cancel-requested state)))

(defn dataset-cancelling?
  [state]
  (or (= :cancelling state)
      (= :cancel-requested state)))

(defn dataset-request-pending?
  [state]
  (or (= :cancel-requested state)
      (= :import-requested state)))

(defn server-status->string
  [server-status]
  (case (:status server-status)
    (nil :ready :done)
    "Ready to use"

    :importing
    (let [progress (:progress server-status)
          progress (when progress (str " " (format-percentage progress)))]
      (case (:state server-status)
        :start "Waiting to start"
        :importing-types "Importing facility types"
        (:request-sites :importing-sites) (str "Importing sites from Resourcemap" progress)
        (:processing-facilities) (str "Pre-processing facilities" progress)
        (:update-projects :updating-projects) "Updating projects"
        "Importing..."))

    :cancelling
    "Cancelling..."

    :unknown
    "Unknown server status"))

(defn import-progress
  [server-status]
  (case (:status server-status)
    (:ready :done)
    1

    :importing
    (let [progress (:progress server-status)]
      (case (:state server-status)
        (:start :importing-types)
        0

        (:request-sites :importing-sites)
        (* progress 0.05)

        (:processing-facilities)
        (+ 0.05 (* 0.95 progress))

        (:update-projects :updating-projects)
        1))

    0))

(defn import-result->string
  [result]
  (case result
    :success "Success"
    :cancelled "Cancelled"
    :unexpected-event "Fatal error: unexpected event received"
    :import-types-failed "Error: failed to import facility types"
    :import-sites-failed "Error: failed to import sites from Resourcemap"
    :update-projects-failed "Error: failed to update projects"
    nil))

(defn server-status->state
  [{status :status}]
  (case status
    (:ready :done) :ready
    :importing     :importing
    :cancelling    :cancelling
    :ready))

(defn resmap-authorised?
  [resmap]
  (:authorised? resmap))

(defn remove-by-id
  [coll id]
  (filter #(not= id (:id %)) coll))

(defn remove-resmap-collection
  [resmap coll-id]
  (update resmap :collections remove-by-id coll-id))

(defn new-dataset-valid?
  [new-dataset-data]
  (and (some? (:collection new-dataset-data))
       (some? (:type-field new-dataset-data))))
