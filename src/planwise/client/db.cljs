(ns planwise.client.db)

(def initial-position-and-zoom {:position [-0.0236 37.9062]
                                :zoom 7})

(def transport-definitions
   {:time (concat
           [{:id (* 60 30) :name "30 minutes"}
            {:id (* 60 45) :name "45 minutes"}
            {:id (* 60 60) :name "1 hour"}]
           (map
            (fn [total-mins]
              (let [hours (quot total-mins 60)
                    mins (rem total-mins 60)]
                {:id (* 60 total-mins), :name (str hours (if (> mins 0) (str ":" mins)) " hours")}))
            (range 75 181 15)))})

(def empty-project-viewmodel
  {:facilities {;; Filters and stats for facilities
                :filters {:type #{}
                          :ownership #{}
                          :services #{}}
                :count 0
                :total 4944
                :list []
                :isochrones nil} ;; geojson string
   :transport {:time nil}
   :map-view {} ;; {:keys position zoom}
   :project-data {}}) ;; {:keys id goal region-id stats filters}

(defn project-viewmodel
  [{:keys [filters stats] :as project-data}]
  (let [facilities-filters (:facilities filters)
        facilities-filters (zipmap (keys facilities-filters)
                                   (map set (vals facilities-filters)))]
    (-> empty-project-viewmodel
       (assoc :project-data project-data)
       (assoc-in [:facilities :filters] facilities-filters)
       (assoc-in [:transport] (:transport filters))
       (assoc-in [:facilities :total] (:facilities-total stats))
       (assoc-in [:facilities :count] (:facilities-targeted stats)))))

(defn project-filters
  [viewmodel]
  {:facilities (get-in viewmodel [:facilities :filters])
   :transport (:transport viewmodel)})

(def empty-datasets-selected
  {:collection nil
                                        ; The ID of the currently selected collection
   :valid?     false
                                        ; If the collection if valid for import
   :fields     nil
                                        ; Fields available for mapping to facility type
   :type-field nil
                                        ; Field selected for mapping facility type
   })

(def initial-db
  {;; Navigation
   :current-page :home

   ;; Filter definitions - eventually this should be requested to the server
   :filter-definitions
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
                       "CEmONC"]}

   ;; Projects
   :projects
   {:view-state :loading ; [:create-dialog :creating :loading :view]
    :list nil
    :search-string ""
    :current empty-project-viewmodel}

   ;; Regions
   :regions {} ;; id => {:keys id name admin_level & geojson}

   ;; Datasets
   :datasets
   {:state nil
                                        ; :initialising/nil :ready :importing
    :raw-status nil
    :facility-count nil
                                        ; Count of available facilities
    :resourcemap {
                  :authorised?  nil
                                        ; Whether the user has authorised for
                                        ; Resourcemap access
                  :collections  nil
                                        ; Resourcemap collections
                  }
    :selected empty-datasets-selected
    }

   ;; Playground related data
   :playground {:map-view initial-position-and-zoom
                :loading? false
                :threshold 3600
                :algorithm "alpha-shape"
                :simplify 0.0
                :node-id nil
                :points []}})
