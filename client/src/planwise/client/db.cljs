(ns planwise.client.db
  (:require [planwise.client.sources.db :as sources]
            [planwise.client.projects2.db :as projects2]
            [planwise.client.scenarios.db :as scenarios]
            [planwise.client.providers-set.db :as providers-set]))

(def initial-db
  {;; Navigation (current page)
   :current-page        :home
   ;; Map of navigation params (ie. :page, :id, :section, etc)
   :page-params         {}

   ;; Projects2
   :projects2           projects2/initial-db

   ;; Regions id => {:keys [id name admin-level & [geojson preview-geojson]]}
   :regions             {}

   ;; Providers Set
   :providers-set       providers-set/initial-db

   :coverage            {}

   :sources             sources/initial-db

   :scenarios           scenarios/initial-db})
