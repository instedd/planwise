(ns planwise.client.projects.api
  (:require [ajax.core :refer [GET POST]]
            [planwise.client.api :refer [json-request]]))

(defn- process-filters [filters]
  ; TODO: Make the set->vector conversion generic and move it into an interceptor
  (let [processed-filters (into {} (for [[k v] filters] [k (if (set? v) (apply vector v) v)]))]
    (if (seq (:type filters))
      processed-filters
      ;; bogus facility type ID to reject all facilities
      (assoc processed-filters :type ["0"]))))


(defn load-project [id & handlers]
  (GET
    (str "/api/projects/" id)
    (json-request {:id id} handlers)))

(defn load-projects [& handlers]
  (GET
    "/api/projects/"
    (json-request {} handlers)))

(defn create-project [params & handlers]
  (POST
    "/api/projects/"
    (json-request params handlers)))

(defn fetch-facilities [filters & handlers]
  (GET
     "/api/facilities/"
     (json-request (process-filters filters) handlers)))

(defn fetch-facilities-with-isochrones [filters isochrone-options & handlers]
  (GET
    "/api/facilities/with-isochrones"
    (json-request (merge isochrone-options (process-filters filters)) handlers)))

(defn fetch-facility-types [& handlers]
  (GET "/api/facilities/types" (json-request {} handlers)))
