(ns planwise.endpoint.scenarios
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.spec.alpha :as s]
            [ring.util.response :refer [response status not-found header content-type file-response]]
            [planwise.util.ring :as util]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [clojure.core.reducers :as r]
            [planwise.engine.common :as common]
            [planwise.boundary.scenarios :as scenarios]
            [planwise.boundary.projects2 :as projects2]))

(timbre/refer-timbre)

(defn- filter-owned-by
  [project owner-id]
  (when (= (:owner-id project) owner-id) project))

(defn- scenarios-routes
  [{service :scenarios projects2 :projects2}]
  [service]
  (routes
   (GET "/:id" [id :as request]
     (let [user-id  (util/request-user-id request)
           id       (Integer. id)
           {:keys [project-id] :as scenario} (scenarios/get-scenario service id)
           project  (filter-owned-by (projects2/get-project projects2 project-id) user-id)]
       (if (or (nil? project) (nil? scenario))
         (not-found {:error "Scenario not found"})
         (response (scenarios/get-scenario-for-project service scenario project)))))

   (GET "/:id/providers" [id :as request]
     (let [user-id  (util/request-user-id request)
           id       (Integer. id)
           {:keys [project-id] :as scenario} (scenarios/get-scenario service id)
           project  (filter-owned-by (projects2/get-project projects2 project-id) user-id)
           csv-name (str project-id "-" id "-" (:name scenario) ".providers.csv")
           csv-data (scenarios/export-providers-data service project scenario)]
       (-> (response csv-data)
           (content-type "text/csv")
           (header "Content-Disposition" (str "attachment; filename=" csv-name)))))

   (GET "/:id/sources" [id :as request]
     (let [user-id  (util/request-user-id request)
           id       (Integer. id)
           {:keys [project-id] :as scenario} (scenarios/get-scenario service id)
           project  (filter-owned-by (projects2/get-project projects2 project-id) user-id)]
       (if (or (nil? project) (nil? scenario))
         (not-found {:error "Scenario not found"})
         (if (common/is-project-raster? project)
           (let [tif-name (str project-id "-" id "-" (:name scenario) ".source.tif")
                 tif-file (common/scenario-raster-full-path (:raster scenario))]
             (-> (file-response tif-file)
                 (content-type "image/tiff")
                 (header "Content-Disposition" (str "attachment; filename=" tif-name))))
           (let [csv-name (str project-id "-" id "-" (:name scenario) ".sources.csv")
                 csv-data (scenarios/export-sources-data service project scenario)]
             (-> (response csv-data)
                 (content-type "text/csv")
                 (header "Content-Disposition" (str "attachment; filename=" csv-name))))))))

   (GET "/:id/suggested-locations" [id :as request]
     (let [user-id  (util/request-user-id request)
           id       (Integer. id)
           {:keys [project-id] :as scenario} (scenarios/get-scenario service id)
           project  (filter-owned-by (projects2/get-project projects2 project-id) user-id)
           result   (scenarios/get-suggestions-for-new-provider-location service project scenario)]
       (if (empty? result)
         (not-found {:error "Can not find optimal locations"})
         (response result))))

   (GET "/:id/suggested-providers" [id :as request]
     (let [user-id  (util/request-user-id request)
           id       (Integer. id)
           {:keys [project-id] :as scenario} (scenarios/get-scenario service id)
           project  (filter-owned-by (projects2/get-project projects2 project-id) user-id)
           result   (scenarios/get-suggestions-for-improving-providers service project scenario)]
       (if (empty? result)
         (not-found {:error "Can not find optimal improvements"})
         (response result))))

   (GET "/:id/geometry/:provider-id" [id provider-id :as request]
     (let [user-id  (util/request-user-id request)
           id       (Integer. id)
           {:keys [project-id] :as scenario} (scenarios/get-scenario service id)
           project  (filter-owned-by (projects2/get-project projects2 project-id) user-id)
           geometry (scenarios/get-provider-geom service project scenario provider-id)]
       (response geometry)))

   (PUT "/:id" [id scenario :as request]
     (let [user-id    (util/request-user-id request)
           id         (Integer. id)
           scenario   (assoc scenario :id id)
           project-id (:project-id (scenarios/get-scenario service id))
           project    (filter-owned-by (projects2/get-project projects2 project-id) user-id)]
       (if (or (nil? project) (nil? scenario))
         (not-found {:error "Scenario not found"})
         (do
           (scenarios/update-scenario service project scenario)
           (response (dissoc (scenarios/get-scenario service id) scenario :updated-at))))))

   (POST "/:id/copy" [id :as request]
     (let [user-id    (util/request-user-id request)
           id         (Integer. id)
           scenario   (scenarios/get-scenario service id)
           project-id (:project-id scenario)
           project    (filter-owned-by (projects2/get-project projects2 project-id) user-id)
           {:keys [name changeset]} scenario
           next-name  (scenarios/next-scenario-name service project-id name)]
       (if (or (nil? project) (nil? scenario))
         (not-found {:error "Scenario not found"})
         (response (scenarios/create-scenario service project {:name next-name
                                                               :changeset changeset})))))

   (DELETE "/:id" [id :as request]
     (let [user-id           (util/request-user-id request)
           scenario-id       (Integer. id)]
       (try
         (scenarios/delete-scenario service scenario-id)
         {:status 204}
         (catch Exception e
           {:status 400}))))))

(defn scenarios-endpoint
  [config]
  (context "/api/scenarios" []
    (restrict (scenarios-routes config) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/scenarios
  [_ config]
  (scenarios-endpoint config))
