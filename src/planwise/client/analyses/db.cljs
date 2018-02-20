(ns planwise.client.analyses.db
  (:require [schema.core :as s :include-macros true]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by-id]]))

(s/defschema Analysis
  "Information pertaining a single analysis"
  {:id   s/Int
   :name s/Str})

(s/defschema AnalysesViewModel
  "Analyses related portion of the client database"
  {:list             (asdf/Asdf (s/maybe [Analysis]))})

(def initial-db
  {:list             (asdf/new nil)       ; List of available analyses
   })

