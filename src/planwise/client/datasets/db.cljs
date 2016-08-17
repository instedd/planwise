(ns planwise.client.datasets.db
  (:require [schema.core :as s :include-macros true]
            [planwise.client.utils :refer [format-percentage]]))

(s/defschema ServerStatus
  {:status                    (s/enum :ready :done :importing :cancelling :unknown)
   (s/optional-key :state)    s/Keyword
   (s/optional-key :result)   s/Any
   (s/optional-key :progress) s/Any})

(s/defschema Datasets
  "Datasets related portion of the client database"
  {:state          (s/enum nil
                           :initialising
                           :ready
                           :import-requested
                           :importing
                           :cancel-requested
                           :cancelling)
   :server-status  (s/maybe ServerStatus)
   :facility-count (s/maybe s/Int)
   :resourcemap    {:authorised? (s/maybe s/Bool)
                    :collections s/Any}
   :selected       s/Any})

(def empty-datasets-selected
  {:collection nil
                                        ; The ID of the currently selected collection
   :valid?     false
                                        ; If the collection if valid for import
   :fields     nil
                                        ; Fields available for mapping to facility type
   :type-field nil})
                                        ; Field selected for mapping facility type

(def initial-db
  {:state          nil
                                        ; :initialising/nil :ready :importing
   :server-status  nil
                                        ; Importer status as reported by the server
   :facility-count nil
                                        ; Count of available facilities
   :resourcemap    {:authorised?  nil
                                        ; Whether the user has authorised for
                                        ; Resourcemap access
                    :collections  nil}
                                        ; Resourcemap collections

   :selected empty-datasets-selected})


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
