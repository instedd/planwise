(ns planwise.client.db
  (:require [planwise.client.datasets.db :as datasets]
            [planwise.client.projects.db :as projects]
            [planwise.client.playground.db :as playground]))

(def sample-filters
  {:facility-type [{:value 1 :label "Dispensary"}
                   {:value 2 :label "Health Center"}
                   {:value 3 :label "Hospital"}
                   {:value 4 :label "General Hospital"}]

   :facility-ownership ["MOH"
                        "Faith Based Organization"
                        "NGO"
                        "Private"]

   :facility-service ["Audiology"
                      "Cardiac Services Unit"
                      "Diabetes and Endocrinology"
                      "Haematology"
                      "BEmONC"
                      "CEmONC"]})

(def initial-db
  {;; Navigation
   :current-page        :home

   ;; Filter definitions - these are replaced by requests to the server
   :filter-definitions  sample-filters

   ;; Projects
   :projects            projects/initial-db

   ;; Regions id => {:keys id name admin-level & geojson}
   :regions             {}

   ;; Datasets
   :datasets            datasets/initial-db

   ;; Playground related data
   :playground          playground/initial-db})
