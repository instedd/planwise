(ns planwise.endpoint.datasets
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as timbre]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response status]]
            [clojure.set :refer [rename-keys]]
            [planwise.util.ring :as util]
            [planwise.boundary.facilities :as facilities]
            [planwise.boundary.datasets :as datasets]
            [planwise.component.importer :as importer]
            [planwise.component.resmap :as resmap]))

(timbre/refer-timbre)

(defn- usable-field?
  [field]
  (#{"select_one"} (:kind field)))

(defn- datasets-routes
  [{:keys [datasets facilities resmap importer]}]
  (routes
   (GET "/" request
     (let [user-id (util/request-user-id request)
           sets (datasets/list-datasets-for-user datasets user-id)]
       (response {:datasets sets})))

   (GET "/info" request
     (let [user (:identity request)
           facility-count (facilities/count-facilities facilities)
           authorised? (resmap/authorised? resmap user)
           importer-status (importer/status importer)
           collections (when authorised?
                         (resmap/list-user-collections resmap user))]
       (response {:authorised? authorised?
                  :status importer-status
                  :facility-count facility-count
                  :collections collections})))

   (GET "/status" request
     (let [importer-status (importer/status importer)]
       (response importer-status)))

   (GET "/collection-info/:coll-id" [coll-id :as request]
     (let [user (:identity request)
           fields (resmap/list-collection-fields resmap user coll-id)
           usable-fields (filter usable-field? fields)
           valid? (not (empty? usable-fields))]
       (response {:fields usable-fields
                  :valid? valid?})))

   (POST "/import" [coll-id type-field :as request]
     (info "Will import collection" coll-id "using" type-field "as the facility type")
     (let [user (:identity request)
           fields (resmap/list-collection-fields resmap user coll-id)
           type-field (some (fn [field]
                              (when (= (:id field) type-field)
                                field))
                            fields)
           [result status] (importer/import-collection importer user coll-id type-field)]
       (if (= :ok result)
         (response status)
         (-> (response {:result :error :payload status})
             (status 400)))))

   (POST "/cancel" request
     (info "Cancelling import process by user request")
     (response (importer/cancel-import! importer)))))

(defn datasets-endpoint
  [service]
  (context "/api/datasets" []
    (restrict (datasets-routes service) {:handler authenticated?})))
