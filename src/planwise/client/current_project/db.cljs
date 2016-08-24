(ns planwise.client.current-project.db
  (:require [schema.core :as s]
            [re-frame.utils :as c]
            [clojure.string :as string]
            [planwise.client.asdf :as asdf]
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

(s/defschema ProjectFilters
  {:facilities {:type (s/maybe [s/Int])}
   :transport  {:time (s/maybe s/Num)}})

(s/defschema ProjectStats
  {:facilities-total    s/Num
   :facilities-targeted s/Num})

(s/defschema ProjectShare
  {:user-id     s/Int
   :user-email  s/Str
   :project-id  s/Int})

(s/defschema ProjectData
  {:id                    s/Int
   :goal                  s/Str
   :dataset-id            s/Int
   :filters               ProjectFilters
   :owner-id              s/Int
   :region-id             s/Int
   :region-name           s/Str
   :region-area-km2       s/Num
   :region-max-population s/Num
   :stats                 ProjectStats})

;; An empty view model for the currently selected project
(def initial-db
  {;; Filter definitions - these are replaced by requests to the server
   ;; {:filter-name [{:id 123 :label "One, two, three"}]}
   :filter-definitions {}

   :facilities         {:list       nil           ; array
                        :isochrones {}}           ; threshold => level => id => geojson
   :map-view           {}                         ; {:keys position zoom}

   :map-key            (asdf/new nil)
   :unsatisfied-demand nil

   :map-state          {:current :loaded          ; [:loaded :request-pending :loading :loading-displayed]
                        :timeout nil
                        :request nil
                        :request-with nil}

   :view-state         :project                   ; [:project :share-dialog]

   :shares             (asdf/new [])              ; [ProjectShare]
   :sharing            {:emails-text nil
                        :token (asdf/new "")
                        :state nil                ; [nil :sending :sent]
                        :shares-search-string ""}

   :dataset            (asdf/new nil)

   :project-data       nil                      ; see ProjectData above
   :wizard             {
                        :set false
                        :tabs {
                               :demographics    :unvisited
                               :facilities      :unvisited
                               :transport-means :unvisited}}})


;; Project data manipulation functions

(defn- update-viewmodel-associations
  [viewmodel {:keys [facilities shares share-token map-key unsatisfied-count]}]
  (as-> viewmodel vm
    (if (some? facilities)
      (assoc-in vm [:facilities :list] facilities)
      vm)
    (if (some? shares)
      (update vm :shares asdf/reset! shares)
      vm)
    (update-in vm [:sharing :token] asdf/reset! share-token)
    (update-in vm [:map-key] asdf/reset! map-key)
    (assoc vm :unsatisfied-demand unsatisfied-count)))

(defn update-viewmodel
  [viewmodel project-data]
  (-> viewmodel
      (update :project-data merge (dissoc project-data :facilities :shares :share-token))
      (update-viewmodel-associations project-data)))

(defn new-viewmodel
  [project-data]
  (update-viewmodel initial-db project-data))

(defn project-id
  [viewmodel]
  (get-in viewmodel [:project-data :id]))

(defn dataset-id
  [viewmodel]
  (get-in viewmodel [:project-data :dataset-id]))

(defn project-filters
  [viewmodel]
  (get-in viewmodel [:project-data :filters]))

(defn facilities-criteria
  [viewmodel]
  (let [project-data (:project-data viewmodel)
        filters (get-in project-data [:filters :facilities])
        project-dataset-id (:dataset-id project-data)
        project-region-id (:region-id project-data)]
    (assoc filters
           :dataset-id project-dataset-id
           :region project-region-id)))

(defn show-share-dialog?
  [view-state]
  (#{:share-dialog} view-state))

(defn split-emails
  [text]
  (filter seq (string/split text #"[,;\s]+")))
