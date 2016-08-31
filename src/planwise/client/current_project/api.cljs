(ns planwise.client.current-project.api
  (:require [ajax.core :refer [GET POST PUT DELETE]]
            [re-frame.utils :as c]
            [planwise.client.api :refer [json-request]]))


;; Project loading and updating

(defn load-project [id with-data & handlers]
  (let [url (str "/api/projects/" id)
        params {:with (some-> with-data name)}]
    (GET url (json-request params handlers))))

(defn access-project [id token with-data & handlers]
  (let [url (str "/api/projects/" id "/access/" token)
        params {:with (some-> with-data name)}]
    (POST url (json-request params handlers))))

(defn update-project [project-id filters with-data & handlers]
  (let [url (str "/api/projects/" project-id)
        params {:id project-id
                :filters filters
                :with (some-> with-data name)}]
    (PUT url (json-request params handlers))))

;; Project sharing

(defn reset-share-token [project-id & handlers]
  (POST
    (str "/api/projects/" project-id "/token/reset")
    (json-request {} handlers)))

(defn delete-share [id user-id & handlers]
  (DELETE
    (str "/api/projects/" id "/shares/" user-id)
    (json-request {} handlers)))

;; Dataset related APIs

(defn fetch-facility-types [dataset-id & handlers]
  (GET "/api/facilities/types" (json-request {:dataset-id dataset-id} handlers)))


;; Facilities related APIs

(defn- process-filters [filters]
  "Force the presence of the type filter in case there is none selected,
  otherwise the server will not filter by type altogether"
  (if (seq (:type filters))
    filters
    (assoc filters :type "")))

(defn fetch-facilities [filters & handlers]
  (GET
      "/api/facilities/"
      (json-request (process-filters filters) handlers)))

(defn fetch-isochrones-in-bbox [filters isochrone-options & handlers]
  (POST
      "/api/facilities/bbox-isochrones"
      (json-request
       (merge isochrone-options (process-filters filters))
       handlers
       :mapper-fn (partial merge isochrone-options))))
