(ns planwise.client.projects.db
  (:require [re-frame.utils :as c]
            [planwise.client.mapping :as maps]))

;; Default data structures

(def initial-position-and-zoom {:position [-0.0236 37.9062]
                                :zoom 7})

;; Filter definitions for transport means (by car)
(def transport-definitions
  {:time (concat
          [{:id (* 60 30) :name "30 minutes"}
           {:id (* 60 45) :name "45 minutes"}
           {:id (* 60 60) :name "1 hour"}]
          (map
           (fn [total-mins]
             (let [hours (quot total-mins 60)
                   mins (rem total-mins 60)]
               {:id (* 60 total-mins)
                :name (str hours (if (> mins 0) (str ":" mins)) " hours")}))
           (range 75 181 15)))})

;; An empty view model for the currently selected project
(def empty-viewmodel
  {:facilities   {;; Filters and stats for facilities
                  :filters    {:type      #{}
                               :ownership #{}
                               :services  #{}}
                  :count      0
                  :total      0
                  :list       nil  ;; array
                  :isochrones {}}  ;; threshold => level => id => geojson
   :transport      {:time       nil}
   :map-view       {} ;; {:keys position zoom}

   :map-state      {:current :loaded ;; [:loaded :request-pending :loading :loading-displayed]
                    :timeout nil
                    :request nil
                    :request-with nil}

   :demand-map-key nil ;; string
   :unsatisfied-count nil ;; number
   :project-data   {}}) ;; {:keys id goal region-id stats filters region-population region-area-km2}


(def initial-db
  {:view-state    :loading ; [:create-dialog :creating :loading :view]
   :list          nil
   :search-string ""
   :current       empty-viewmodel})


;; Project data manipulation functions

(defn- update-viewmodel-associations
  [viewmodel {:keys [facilities]}]
  (cond
    facilities
    (assoc-in viewmodel [:facilities :list] facilities)
    :else
    viewmodel))

(defn update-viewmodel
  [viewmodel {:keys [filters stats] :as project-data}]
  (let [facilities-filters (:facilities filters)
        facilities-filters (zipmap (keys facilities-filters)
                                   (map set (vals facilities-filters)))]
    (-> viewmodel
        (assoc :project-data project-data)
        (assoc :demand-map-key (:map-key project-data))
        (assoc :unsatisfied-count (:unsatisfied-count project-data))
        (assoc-in [:facilities :filters] facilities-filters)
        (assoc-in [:transport] (:transport filters))
        (assoc-in [:facilities :total] (:facilities-total stats))
        (assoc-in [:facilities :count] (:facilities-targeted stats))
        (update-viewmodel-associations project-data))))

(defn new-viewmodel
  [project-data]
  (update-viewmodel empty-viewmodel project-data))

; REFACTOR: project-filters is used in update-project, while facilities-criteria in fetch-* methods; unify them
(defn project-filters
  [viewmodel]
  {:facilities (get-in viewmodel [:facilities :filters])
   :transport (:transport viewmodel)})

(defn facilities-criteria
 [viewmodel]
 (let [filters (get-in viewmodel [:facilities :filters])
       project-region-id (get-in viewmodel [:project-data :region-id])]
   (assoc filters :region project-region-id)))
