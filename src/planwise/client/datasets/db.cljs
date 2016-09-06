(ns planwise.client.datasets.db
  (:require [schema.core :as s :include-macros true]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [format-percentage remove-by-id]]))

(s/defschema ServerStatus
  {:status                    (s/enum :ready :done :importing :cancelling :unknown)
   (s/optional-key :state)    s/Keyword
   (s/optional-key :progress) s/Any})

;; TODO: we should probably ditch the Dataset state concept altogether and use
;; the server reported status always; doing so we do lose the state
;; cancel-requested, but we're not using it right now anyway.

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


;; TODO: review names and parameters of these utility functions and predicates

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

(defn remove-resmap-collection
  [resmap coll-id]
  (update resmap :collections remove-by-id coll-id))

(defn new-dataset-valid?
  [new-dataset-data]
  (and (some? (:collection new-dataset-data))
       (some? (:type-field new-dataset-data))))

(defn dataset->warnings
  "Returns a map of the warnings yielded in the import result of the dataset"
  [{import-result :import-result}]
  (select-keys import-result [:facilities-outside-regions-count
                              :facilities-without-road-network-count
                              :sites-without-location-count]))

(defn dataset->status
  "Returns one of importing, cancelled, success, warn or unknown, based on the dataset server status and import result"
  [{:keys [server-status import-result], :as dataset}]
  (let [warnings? (some->> dataset (dataset->warnings) (vals) (apply +) (pos?))]
    (when dataset
      (case (keyword (:status server-status))
        :importing    :importing
        :cancelling   :cancelled
        :unknown      :unknown

        (case (keyword (:result import-result))
          :cancelled    :cancelled
          :success      (if warnings? :warn :success)
          :error)))))

(defn dataset->warning-text
  "Returns a warning text if the dataset status is importing, cancelled, unknown or error"
  [dataset]
  (case (dataset->status dataset)
    :importing "This dataset is still being imported. Your project's data may be incomplete or inconsistent until the process finishes."
    :cancelled "The import process for this dataset was cancelled. Your project's data may be incomplete or inconsistent."
    :unknown   "The status for this dataset is unknown. Your project's data may be incomplete or inconsistent."
    :error     "The import process for this dataset has failed. Your project's data may be incomplete or inconsistent."
    nil))

(defn dataset->status-icon
  "Returns a status icon relative to the dataset status"
  [dataset]
  (case (dataset->status dataset)
    (nil :success :importing) :location
    (:cancelled :warn)        :warning
    (:error :unknown)         :remove-circle
    nil))

(defn dataset->status-class
  "Returns a status class relative to the dataset status"
  [dataset]
  (case (dataset->status dataset)
    (nil :success :importing) nil
    (:error :unknown)         "error"
    :warn                     "warning"
    :cancelled                "cancelled"
    nil))
