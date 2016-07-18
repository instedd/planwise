(ns planwise.client.db)

(def initial-position-and-zoom {:position [-1.29 36.83]
                                :zoom 9})

(def initial-db
  {;; Navigation
   :current-page :home

   ;; Filter definitions - eventually this should be requested to the server
   :filter-definitions
   {:facility-type ["Dispensary"
                    "Health Center"
                    "Hospital"
                    "General Hospital"]

    :facility-ownership ["MOH"
                         "Faith Based Organization"
                         "NGO"
                         "Private"]

    :facility-service ["Audiology"
                       "Cardiac Services Unit"
                       "Diabetes and Endocrinology"
                       "Haematology"
                       "BEmONC"
                       "CEmONC"]}

   ;; Projects
   :projects
   {:view-state :loading ; [:create-dialog :creating :loading :view]
    :current nil
    :list nil
    :search-string ""}

   ;; Project currently viewing/editing
   :current-project
   {:facilities {;; Filters and stats for facilities
                 :filters {:type #{}
                           :ownership #{}
                           :services #{}}
                 :count 0
                 :total 4944
                 :list []}
    :map-view initial-position-and-zoom}

   ;; Datasets
   :datasets
   {:initialised? false
    :facility-count nil
                                        ; Count of available facilities
    :resourcemap {
                  :authorised?  nil
                                        ; Whether the user has authorised for
                                        ; Resourcemap access
                  :collections  nil
                                        ; Resourcemap collections
                  }
    }

   ;; Playground related data
   :playground {:map-view initial-position-and-zoom
                :loading? false
                :threshold 3600
                :algorithm "alpha-shape"
                :simplify 0.0
                :node-id nil
                :points []}})
