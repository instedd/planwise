(ns planwise.client.db
  (:require [planwise.client.datasets.db :as datasets]
            [planwise.client.projects.db :as projects]
            [planwise.client.current-project.db :as current-project]))

(def initial-db
  {;; Navigation (current page)
   :current-page        :home
   ;; Map of navigation params (ie. :page, :id, :section, etc)
   :page-params         {}

   ;; Projects
   :projects            projects/initial-db

   ;; Currently selected project
   :current-project     current-project/initial-db

   ;; Regions id => {:keys [id name admin-level & [geojson preview-geojson]]}
   :regions             {}

   ;; Datasets
   :datasets            datasets/initial-db})
