(ns planwise.client.db
  (:require [planwise.client.datasets.db :as datasets]
            [planwise.client.population :as population]
            [planwise.client.projects.db :as projects]
            [planwise.client.projects2.db :as projects2]
            [planwise.client.scenarios.db :as scenarios]
            [planwise.client.providers-set.db :as providers-set]
            [planwise.client.current-project.db :as current-project]))

(def initial-db
  {;; Navigation (current page)
   :current-page        :home
   ;; Map of navigation params (ie. :page, :id, :section, etc)
   :page-params         {}

   ;; Projects
   :projects            projects/initial-db

   ;; Projects2
   :projects2           projects2/initial-db

   ;; Currently selected project
   :current-project     current-project/initial-db

   ;; Regions id => {:keys [id name admin-level & [geojson preview-geojson]]}
   :regions             {}

   ;; Datasets
   :datasets            datasets/initial-db

   ;; Providers set
   :providers-set       providers-set/initial-db

   :coverage            {}

   :population          population/initial-db

   :scenarios           scenarios/initial-db})

