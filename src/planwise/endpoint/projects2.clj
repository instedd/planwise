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
      (default [:actions :budget] nil)))

(s/def ::id number?)
(s/def ::dataset-id (s/nilable number?))
(s/def ::region-id (s/nilable number?))
(s/def ::population-source-id (s/nilable number?))
(s/def ::coverage-algorithm (s/nilable string?))
(s/def ::name string?)
(s/def ::target (s/nilable number?))
(s/def ::budget (s/nilable number?))
(s/def ::demographics (s/keys :req-un [::target]))
(s/def ::actions (s/keys :req-un [::budget]))
(s/def ::config (s/nilable (s/keys :req-un [::demographics ::actions])))
(s/def ::project (s/keys :req-un [::id ::owner-id ::name ::config ::region-id ::dataset-id ::population-source-id ::coverage-algorithm]))

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
           id         (:id project)]
       (assert (s/valid? ::project project) "Invalid project")
       (projects2/update-project service id project)))

   (GET "/:id" [id :as request]
     (let [user-id (util/request-user-id request)
           project (projects2/get-project service (Integer. id))]
       (println project)
       (if (nil? project)
         (not-found project)
         (response (assoc project :config (apply-default (:config project)))))))

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
