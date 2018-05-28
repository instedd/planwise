(ns planwise.client.providers-set.api)

;; ----------------------------------------------------------------------------
;; Utility functions

(defn- map-server-status
  [server-status]
  (some-> server-status
          (update :status keyword)
          (update :state keyword)))

(defn- map-provider-set
  [server-provider-set]
  (update server-provider-set :server-status map-server-status))

;; ----------------------------------------------------------------------------
;; API methods

(def load-providers-set
  {:method    :get
   :uri       "/api/providers-set"
   :mapper-fn (partial map map-provider-set)})

(defn load-provider-set
  [provider-set-id]
  {:method    :get
   :uri       (str "/api/providers-set/" provider-set-id)
   :mapper-fn map-provider-set})

(def load-resourcemap-info
  {:method  :get
   :uri     "/api/providers-set/resourcemap-info"
   :timeout 60000})

(defn create-provider-set!
  [name description coll-id type-field]
  {:method    :post
   :uri       "/api/providers-set"
   :params    {:name        name
               :description description
               :coll-id     coll-id
               :type-field  type-field}
   :mapper-fn map-provider-set})

(defn update-provider-set!
  [id]
  {:method    :post
   :uri       (str "/api/providers-set/" id "/update")
   :mapper-fn map-provider-set})

(defn cancel-import!
  [provider-set-id]
  {:method    :post
   :uri       "/api/providers-set/cancel"
   :params    {:provider-set-id provider-set-id}
   :mapper-fn (partial map map-provider-set)})

(defn delete-provider-set!
  [provider-set-id]
  {:method :delete
   :uri    (str "/api/providers-set/" provider-set-id)})
