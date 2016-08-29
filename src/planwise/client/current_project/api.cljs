(ns planwise.client.current-project.api
  (:require [ajax.core :refer [GET POST PUT]]
            [planwise.client.api :refer [json-request]]))


;; Project loading and updating

(defn load-project [id with-data & handlers]
  (let [url (str "/api/projects/" id)
        params {:id id
                :with (some-> with-data name)}]
    (GET url (json-request params handlers))))

(defn update-project [project-id filters with-data & handlers]
  (let [url (str "/api/projects/" project-id)
        params {:id project-id
                :filters filters
                :with (some-> with-data name)}]
    (PUT url
        (json-request
         params
         handlers
         :mapper-fn (partial merge (dissoc filters :facilities))))))

;; Dataset related APIs

(defn fetch-facility-types [dataset-id & handlers]
  (GET "/api/facilities/types" (json-request {:dataset-id dataset-id} handlers)))


;; Facilities related APIs

(defn- process-filters [filters]
  ;; TODO: Make the set->vector conversion generic and move it into an interceptor
  (let [processed-filters (into {} (for [[k v] filters] [k (if (set? v) (apply vector v) v)]))]
    (if (seq (:type filters))
      processed-filters
      (assoc processed-filters :type ""))))

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

