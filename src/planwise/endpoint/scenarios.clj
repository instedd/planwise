(ns planwise.endpoint.scenarios
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.spec.alpha :as s]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [clojure.core.reducers :as r]
            [planwise.boundary.scenarios :as scenarios]
            [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.projects2 :as projects2]))

(timbre/refer-timbre)

(defn- filter-owned-by
  [project owner-id]
  (when (= (:owner-id project) owner-id) project))

(defn- filter-options
  [{:keys [region-id coverage-algorithm config]}]
  {:region-id          region-id
   :coverage-algorithm coverage-algorithm
   :coverage-options   (get-in config [:coverage :filter-options])
   :tags               (get-in config [:providers :tags])})

(defn- scenarios-routes
  [{service :scenarios projects2 :projects2 providers-set :providers-set}]
  [service]
  (routes
   (GET "/:id" [id :as request]
     (let [user-id                           (util/request-user-id request)
           id                                (Integer. id)
           {:keys [project-id] :as scenario} (scenarios/get-scenario service id)
           {:keys [provider-set-id provider-set-version] :as project} (filter-owned-by (projects2/get-project projects2 project-id) user-id)
           initial-changeset                 (providers-set/get-providers providers-set provider-set-id provider-set-version (filter-options project))]
       (if (or (nil? project) (nil? scenario))
         (not-found {:error "Scenario not found"})
         (response (-> scenario (dissoc :updated-at)
                       (assoc  :changeset initial-changeset))))))

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
                                                               :changeset changeset})))))))

(defn scenarios-endpoint
  [config]
  (context "/api/scenarios" []
    (restrict (scenarios-routes config) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/scenarios
  [_ config]
  (scenarios-endpoint config))
