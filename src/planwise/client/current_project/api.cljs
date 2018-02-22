(ns planwise.client.current-project.api)

;; Project loading and updating

(defn load-project
  [id with-data]
  {:method :get
   :uri    (str "/api/projects/" id)
   :params {:with (some-> with-data name)}})

(defn access-project
  [id token with-data]
  {:method :post
   :uri    (str "/api/projects/" id "/access/" token)
   :params {:with (some-> with-data name)}})

(defn update-project
  [project-id filters with-data]
  {:method :put
   :uri    (str "/api/projects/" project-id)
   :params {:id      project-id
            :filters filters
            :with    (some-> with-data name)}})

(defn update-project-state
  [project-id state]
  {:method :put
   :uri    (str "/api/projects/" project-id)
   :params {:id    project-id
            :state state}})

;; Project sharing

(defn reset-share-token
  [project-id]
  {:method :post
   :uri    (str "/api/projects/" project-id "/token/reset")})

(defn delete-share
  [id user-id]
  {:method :delete
   :uri    (str "/api/projects/" id "/shares/" user-id)})

(defn send-sharing-emails
  [id emails]
  {:method :post
   :uri    (str "/api/projects/" id "/share")
   :params {:emails emails}})


;; Dataset related APIs

(defn fetch-facility-types
  [dataset-id]
  {:method :get
   :uri    "/api/facilities/types"
   :params {:dataset-id dataset-id}})


;; Facilities related APIs

(defn- process-filters
  [filters]
  "Force the presence of the type filter in case there is none selected,
  otherwise the server will not filter by type altogether"
  (if (seq (:type filters))
    filters
    (assoc filters :type "")))

(defn fetch-isochrones-in-bbox
  [filters isochrone-options]
  {:method    :post
   :uri       "/api/facilities/bbox-isochrones"
   :params    (merge isochrone-options (process-filters filters))
   :mapper-fn (partial merge isochrone-options)})
