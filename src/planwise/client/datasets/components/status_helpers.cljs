(ns planwise.client.datasets.components.status-helpers
  (:require [planwise.client.utils :as utils]
            [planwise.client.datasets.db :refer [dataset->status]]))

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
  "Returns a css class relative to the dataset status"
  [dataset]
  (case (dataset->status dataset)
    (nil :success :importing) nil
    (:error :unknown)         "error"
    :warn                     "warning"
    :cancelled                "cancelled"
    nil))
