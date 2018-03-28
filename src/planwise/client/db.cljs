(ns planwise.client.db
  (:require [planwise.client.datasets.db :as datasets]
            [planwise.client.projects.db :as projects]
            [planwise.client.projects2.db :as projects2]
            [planwise.client.datasets2.db :as datasets2]
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

   ;; Datasets2
   :datasets2           datasets2/initial-db

   ;; Datasets
   :datasets            datasets/initial-db

   :coverage            {}

   :population          {}})
