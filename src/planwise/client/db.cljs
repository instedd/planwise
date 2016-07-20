(ns planwise.client.db)

(def initial-position-and-zoom {:position [-0.0236 37.9062]
                                :zoom 7})

(def empty-project-viewmodel
  {:facilities {;; Filters and stats for facilities
                :filters {:type #{}
                          :ownership #{}
                          :services #{}}
                :count 0
                :total 4944
                :list []}
   :map-view {} ;; {:keys position zoom}
   :project-data {}}) ;; {:keys id goal region_id facilities_count}

(defn project-viewmodel [project-data]
  (-> empty-project-viewmodel
      (assoc :project-data project-data)
      (assoc-in [:facilities :total] (:facilities_count project-data))))

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
    :list nil
    :search-string ""
    :current empty-project-viewmodel}

   ;; Regions
   :regions {} ;; id => {:keys id name admin_level & geojson}

   ;; Playground related data
   :playground {:map-view initial-position-and-zoom
                :loading? false
                :threshold 3600
                :algorithm "alpha-shape"
                :simplify 0.0
                :node-id nil
                :points []}})
