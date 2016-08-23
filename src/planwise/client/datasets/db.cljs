(ns planwise.client.datasets.db
  (:require [schema.core :as s :include-macros true]
            [planwise.client.utils :refer [format-percentage]]))

(s/defschema ServerStatus
  {:status                    (s/enum :ready :done :importing :cancelling :unknown)
   (s/optional-key :state)    s/Keyword
   (s/optional-key :result)   s/Any
   (s/optional-key :progress) s/Any})

(s/defschema Dataset
  "Information pertaining a single dataset"
  {:id                             s/Int
   :name                           s/Str
   :description                    s/Str
   :collection-id                  s/Int
   :mappings                       s/Any
   :facility-count                 s/Int
   (s/optional-key :state)         (s/enum :ready
                                           :import-requested
                                           :importing
                                           :cancel-requested
                                           :cancelling)
   (s/optional-key :server-status) (s/maybe ServerStatus)})

(s/defschema ListState
  (s/enum nil
          :loading
          :loaded
          :invalid
          :reloading))

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
  {:state            [(s/one ListState "list")
                      (s/one ViewState "view")]
   :list             (s/maybe [Dataset])
   :search-string    s/Str
   :resourcemap      (s/maybe ResourcemapData)
   :new-dataset-data (s/maybe {(s/optional-key :collection) s/Any
                               (s/optional-key :type-field) s/Any})})

(def initial-db
  {:state            nil
   :list             nil                  ; List of available datasets
   :search-string    ""                   ; Dataset search string

   :resourcemap      nil

   :new-dataset-data nil})


(defn show-dialog?
  [view-state]
  (or (= :create-dialog view-state)
      (= :creating view-state)))

(defn initialised?
  [state]
  (and (not= :initialising state)
       (not (nil? state))))

(defn importing?
  [state]
  (or (= :importing state)
      (= :import-requested state)
      (= :cancelling state)
      (= :cancel-requested state)))

(defn cancelling?
  [state]
  (or (= :cancelling state)
      (= :cancel-requested state)))

(defn request-pending?
  [state]
  (or (= :cancel-requested state)
      (= :import-requested state)))

(defn server-status->string
  [server-status]
  (case (:status server-status)
    :ready
    "Ready"

    :done
    (case (:result server-status)
      :success "Import was successful"
      :cancelled "Import was cancelled"
      "Import was unsuccessful")

    :importing
    (let [progress (:progress server-status)
          progress (when progress (str " (" (format-percentage progress) ")"))]
      (case (:state server-status)
        :start "Starting"
        :importing-types "Importing facility types"
        (:request-sites :importing-sites) (str "Importing sites from Resourcemap" progress)
        (:processing-facilities) (str "Pre-processing facilities" progress)
        (:update-projects :updating-projects) "Updating projects"
        "Importing..."))

    :cancelling
    "Cancelling..."

    :unknown
    "Unknown server status"))

(defn last-import-result
  [{:keys [status result]}]
  (when (and (= :done status) (some? result))
    (case result
      :success "Success"
      :cancelled "Cancelled"
      :unexpected-event "Fatal error: unexpected event received"
      :import-types-failed "Error: failed to import facility types"
      :import-sites-failed "Error: failed to import sites from Resourcemap"
      :update-projects-failed "Error: failed to update projects")))

(defn server-status->state
  [{status :status}]
  (case status
    (:ready :done) :ready
    :importing     :importing
    :cancelling    :cancelling
    :unknown       :ready))

(defn resmap-loaded?
  [resmap]
  (some? (:authorised? resmap)))

(defn resmap-authorised?
  [resmap]
  (:authorised? resmap))

(defn new-dataset-valid?
  [new-dataset-data]
  (and (some? (:collection new-dataset-data))
       (some? (:type-field new-dataset-data))))
