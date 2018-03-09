(ns planwise.client.datasets2.db
  (:require [schema.core :as s :include-macros true]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by-id]]))

(s/defschema Dataset2
  "Information pertaining a single dataset"
  {:id          s/Int
   :name        s/Str})


(s/defschema Datasets2ViewModel
  "Datasets related portion of the client database"
  {:list             (asdf/Asdf (s/maybe [Dataset2]))
   :new-dataset-data (s/maybe {:name s/Str})})

(def initial-db
  {:new-dataset-data nil
   :list             (asdf/new nil)})                                      
