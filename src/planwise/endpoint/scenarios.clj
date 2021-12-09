(ns planwise.endpoint.scenarios
  (:require [compojure.core :refer :all]
            [compojure.coercions :refer [as-int]]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.spec.alpha :as s]
            [ring.util.response :refer [response status not-found header content-type file-response]]
            [planwise.util.ring :as util]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.engine.common :as common]
            [planwise.boundary.scenarios :as scenarios]
            [planwise.boundary.projects2 :as projects2]))

(timbre/refer-timbre)

(defn- scenario-export-prefix
  [{:keys [id project-id name]}]
  (str project-id "-" id "-" name))

(defn- scenario-routes
  [{service :scenarios}]
  (routes
   (GET "/" {:keys [scenario project]}
     (response (scenarios/get-scenario-for-project service scenario project)))

   (PUT "/" [id :<< as-int scenario :as {:keys [project]}]
     (let [scenario (assoc scenario :id id)]
       (scenarios/update-scenario service project scenario)
       (response (dissoc (scenarios/get-scenario service id) scenario :updated-at))))

   (DELETE "/" [id :<< as-int]
     (try
       (scenarios/delete-scenario service id)
       {:status 204}
       (catch Exception e
         {:status 400})))

   (POST "/copy" {:keys [scenario project]}
     (let [{:keys [project-id name changeset]} scenario

           next-name    (scenarios/next-scenario-name service project-id name)
           new-scenario (scenarios/create-scenario service project {:name      next-name
                                                                    :changeset changeset})]
       (response new-scenario)))

   (GET "/providers" {:keys [scenario project]}
     (let [csv-name (str (scenario-export-prefix scenario) ".providers.csv")
           csv-data (scenarios/export-providers-data service project scenario)]
       (-> (response csv-data)
           (content-type "text/csv")
           (header "Content-Disposition" (str "attachment; filename=" csv-name)))))

   (GET "/sources" {:keys [scenario project]}
     (if (common/is-project-raster? project)
       (let [tif-name (str (scenario-export-prefix scenario) ".source.tif")
             tif-file (common/scenario-raster-full-path (:raster scenario))]
         (-> (file-response tif-file)
             (content-type "image/tiff")
             (header "Content-Disposition" (str "attachment; filename=" tif-name))))
       (let [csv-name (str (scenario-export-prefix scenario) ".sources.csv")
             csv-data (scenarios/export-sources-data service project scenario)]
         (-> (response csv-data)
             (content-type "text/csv")
             (header "Content-Disposition" (str "attachment; filename=" csv-name))))))

   (GET "/suggested-locations" {:keys [scenario project]}
     (let [result (scenarios/get-suggestions-for-new-provider-location service project scenario)]
       (if (empty? result)
         (not-found {:error "Can not find optimal locations"})
         (response result))))

   (GET "/suggested-providers" {:keys [scenario project]}
     (let [result (scenarios/get-suggestions-for-improving-providers service project scenario)]
       (if (empty? result)
         (not-found {:error "Can not find optimal improvements"})
         (response result))))

   (GET "/geometry/:provider-id" [provider-id :as {:keys [scenario project]}]
     (if-let [geometry (scenarios/get-provider-geom service project scenario provider-id)]
       (response geometry)
       (not-found {:error "Provider coverage not found"})))))

(defn- wrap-fetch-scenario
  [handler {:keys [scenarios projects2]}]
  (fn [request]
    (let [user-id  (util/request-user-id request)
          id       (-> request :params :id as-int)
          scenario (scenarios/get-scenario scenarios id)
          project  (projects2/get-project projects2 (:project-id scenario))]
      (if (or (nil? project) (nil? scenario) (not= user-id (:owner-id project)))
        (not-found {:error "Scenario not found"})
        (handler (merge request {:scenario scenario
                                 :project  project}))))))

(defn scenarios-endpoint
  [config]
  (context "/api/scenarios/:id" []
    (-> (scenario-routes config)
        (wrap-fetch-scenario config)
        (restrict {:handler authenticated?}))))

(defmethod ig/init-key :planwise.endpoint/scenarios
  [_ config]
  (scenarios-endpoint config))
