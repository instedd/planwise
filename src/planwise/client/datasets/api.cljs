(ns planwise.client.datasets.api)

;; ----------------------------------------------------------------------------
;; Utility functions

(defn- map-server-status
  [server-status]
  (some-> server-status
          (update :status keyword)
          (update :state keyword)))

(defn- map-dataset
  [server-dataset]
  (update server-dataset :server-status map-server-status))

;; ----------------------------------------------------------------------------
;; API methods

(def load-datasets
  {:method    :get
   :uri       "/api/datasets"
   :mapper-fn (partial map map-dataset)})

(defn load-dataset
  [dataset-id]
  {:method    :get
   :uri       (str "/api/datasets/" dataset-id)
   :mapper-fn map-dataset})

(def load-resourcemap-info
  {:method  :get
   :uri     "/api/datasets/resourcemap-info"
   :timeout 60000})

(defn create-dataset!
  [name description coll-id type-field]
  {:method    :post
   :uri       "/api/datasets"
   :params    {:name        name
               :description description
               :coll-id     coll-id
               :type-field  type-field}
   :mapper-fn map-dataset})

(defn update-dataset!
  [id]
  {:method    :post
   :uri       (str "/api/datasets/" id "/update")
   :mapper-fn map-dataset})

(defn cancel-import!
  [dataset-id]
  {:method    :post
   :uri       "/api/datasets/cancel"
   :params    {:dataset-id dataset-id}
   :mapper-fn (partial map map-dataset)})

(defn delete-dataset!
  [dataset-id]
  {:method :delete
   :uri    (str "/api/datasets/" dataset-id)})
