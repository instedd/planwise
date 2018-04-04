(ns planwise.endpoint.projects2
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.spec.alpha :as s]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [clojure.core.reducers :as r]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.model.ident :as ident]
            [planwise.component.datasets2 :as datasets2]
            [planwise.component.projects2 :as projects2]))

(timbre/refer-timbre)

(defn- default
  [map path value-default]
  (let [value (get-in map path)]
    (if (nil? value)
      (assoc-in map path value-default)
      map)))

(defn- apply-default
  [config]
  (-> config
    (default [:demographics :target] nil)
    (default [:actions :budget] nil)
    (default [:coverage :filter-options] {})))

(s/def ::id number?)
(s/def ::dataset-id (s/nilable number?))
(s/def ::region-id (s/nilable number?))
(s/def ::population-source-id (s/nilable number?))
(s/def ::name string?)
(s/def ::target (s/nilable number?))
(s/def ::budget (s/nilable number?))
(s/def ::demographics (s/keys :req-un [::target]))
(s/def ::actions (s/keys :req-un [::budget]))
(s/def ::filter-options map?)
(s/def ::coverage (s/keys :req-un [::filter-options]))
(s/def ::config (s/nilable (s/keys :req-un [::demographics ::actions ::coverage])))
(s/def ::project (s/keys :req-un [::id ::owner-id ::name ::config ::region-id ::dataset-id ::population-source-id]))

(defn- projects2-routes
  [service]
  (routes

   (POST "/" request
     (let [user-id    (util/request-user-id request)
           project-id (:id (projects2/create-project service user-id))
           project    (projects2/get-project service (Integer. project-id))]
      (response project)))

   (PUT "/:id" [id project :as request]
     (let [user-id    (util/request-user-id request)
           {:keys [id dataset-id]} project
           coverage-algorithm (:coverage-algorithm (datasets2/get-dataset service dataset-id))]
       (assert (s/valid? ::project project) "Invalid project")
       (projects2/update-project service id project)
       (response (assoc project :coverage-algorithm coverage-algorithm))))

   (GET "/:id" [id :as request]
     (let [user-id (util/request-user-id request)
           project (projects2/get-project service (Integer. id))
           coverage-algorithm (:coverage-algorithm (datasets2/get-dataset service (:dataset-id project)))]
       (if (nil? project)
           (not-found project)
           (response (-> project (assoc :config (apply-default (:config project)))
                                 (assoc :coverage-algorithm coverage-algorithm))))))

   (GET "/" request
     (let [user-id          (util/request-user-id request)
           list-of-projects (projects2/list-projects service user-id)]
       (response list-of-projects)))))


(defn projects2-endpoint
  [{service :projects2}]
  (context "/api/projects2" []
    (restrict (projects2-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/projects2
  [_ config]
  (projects2-endpoint config))
