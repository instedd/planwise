(ns planwise.endpoint.datasets
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [clojure.core.reducers :as r]
            [planwise.model.ident :as ident]
            [planwise.boundary.facilities :as facilities]
            [planwise.boundary.datasets :as datasets]
            [planwise.model.datasets :as model]
            [planwise.component.importer :as importer]
            [planwise.component.resmap :as resmap]))

(timbre/refer-timbre)

(defn- usable-field?
  [field]
  (#{"select_one"} (:kind field)))

(defn find-usable-collections
  [resmap user-ident exclude-ids]
  (let [exclude-ids (set exclude-ids)
        collections (resmap/list-user-collections resmap user-ident)
        collections (filter #(not (exclude-ids (:id %))) collections)
        with-usable-fields (fn [coll]
                             (let [coll-id (:id coll)
                                   fields (resmap/list-collection-fields resmap user-ident coll-id)
                                   usable-fields (filterv usable-field? fields)]
                               (when (seq usable-fields)
                                 (assoc coll :fields usable-fields))))]
    (into [] (r/filter some? (r/map with-usable-fields collections)))))

(defn add-status-to-datasets
  [sets status]
  (map (fn [dataset]
         (let [dataset-id (:id dataset)
               dataset-status (get status dataset-id)]
           (assoc dataset :server-status dataset-status)))
       sets))

(defn- datasets-routes
  [{:keys [datasets facilities resmap importer]}]
  (routes
   (GET "/" request
     (let [user-id (util/request-user-id request)
           sets (datasets/list-datasets-for-user datasets user-id)
           status (importer/status importer)]
       (response (add-status-to-datasets sets status))))

   (GET "/:id{[0-9]+}" [id :as request]
     (let [id (Integer. id)
           user-id (util/request-user-id request)
           dataset (datasets/find-dataset datasets id)
           status (importer/status importer)]
       (if (or (model/owned-by? dataset user-id)
               (datasets/accessible-by? datasets id user-id))
         (response (assoc dataset :server-status (get status id)))
         (not-found {:error "Dataset not found"}))))

   (GET "/resourcemap-info" request
     (let [user-ident (util/request-ident request)
           user-id (util/request-user-id request)
           authorised? (resmap/authorised? resmap user-ident)
           existing-sets (datasets/list-datasets-for-user datasets user-id)
           exclude-collection-ids (map :collection-id existing-sets)
           collections (when authorised?
                         (find-usable-collections resmap user-ident exclude-collection-ids))]
       (response {:authorised? authorised?
                  :collections collections})))

   (POST "/" [name description coll-id type-field :as request]
     (let [user-ident (util/request-ident request)
           user-email (ident/user-email user-ident)
           user-id (ident/user-id user-ident)
           coll-id (Integer. coll-id)
           type-field (Integer. type-field)
           dataset-templ {:owner-id user-id
                          :name name
                          :description description
                          :collection-id coll-id
                          :import-result nil
                          :mappings {:type type-field}}]
       (info "Creating new dataset of collection" coll-id "for the user" user-email)
       (let [dataset (datasets/create-dataset! datasets dataset-templ)]
         (let [dataset-id (:id dataset)
               [result import-status] (importer/run-import-for-dataset importer (:id dataset) user-ident)
               dataset-status (get import-status dataset-id)]
           (if (= :ok result)
             (response (assoc dataset :server-status dataset-status))
             (-> (response {:error import-status})
                 (status 400)))))))

   (POST "/:id/update" [id :as request]
     (let [id (Integer. id)
           dataset (datasets/find-dataset datasets id)
           user-ident (util/request-ident request)
           user-email (ident/user-email user-ident)
           user-id (ident/user-id user-ident)]
       (if (model/owned-by? dataset user-id)
         (let [[result import-status] (importer/run-import-for-dataset importer id user-ident)
               dataset-status (get import-status id)]
           (info "Updating dataset" id "for the user" user-email)
           (if (= :ok result)
             (response (assoc dataset :server-status dataset-status))
             (-> (response {:error import-status})
                 (status 400))))
         (not-found {:error "Dataset not found"}))))

   (POST "/cancel" [dataset-id :as request]
     (info "Cancelling import process by user request")
     (let [user-id (util/request-user-id request)
           sets (datasets/list-datasets-for-user datasets user-id)
           status (importer/cancel-import! importer dataset-id)]
       (response (add-status-to-datasets sets status))))

   (DELETE "/:id" [id :as request]
     (let [id (Integer. id)
           user-id (util/request-user-id request)
           dataset (datasets/find-dataset datasets id)]
       (if (and (model/owned-by? dataset user-id)
                (datasets/destroy-dataset! datasets id))
         (response {:deleted id})
         (not-found {:error "Dataset not found"}))))))

(defn datasets-endpoint
  [service]
  (context "/api/datasets" []
    (restrict (datasets-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/datasets
  [_ config]
  (datasets-endpoint config))
