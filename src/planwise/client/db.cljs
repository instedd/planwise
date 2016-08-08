(ns planwise.client.db
  (:require [planwise.client.datasets.db :as datasets]
            [planwise.client.projects.db :as projects]
            [planwise.client.playground.db :as playground]))

(def initial-db
  {;; Navigation (current page)
   :current-page        :home
   ;; Map of navigation params (ie. :page, :id, :section, etc)
   :page-params         {}

   ;; Filter definitions - these are replaced by requests to the server
   ;; {:filter-name [{:id 123 :label "One, two, three"}]}
   :filter-definitions  {}

   ;; Projects
   :projects            projects/initial-db

   ;; Regions id => {:keys [id name admin-level & [geojson preview-geojson]]}
   :regions             {}

   ;; Datasets
   :datasets            datasets/initial-db

   ;; Playground related data
   :playground          playground/initial-db})
