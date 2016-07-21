(ns planwise.endpoint.datasets
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as timbre]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response]]
            [clojure.set :refer [rename-keys]]
            [planwise.boundary.facilities :as facilities]
            [planwise.component.importer :as importer]
            [planwise.component.resmap :as resmap]))

(timbre/refer-timbre)

(defn- usable-field?
  [field]
  (#{"select_one"} (:kind field)))

(defn- datasets-routes
  [{:keys [facilities resmap importer]}]
  (routes
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
       (response {:status importer-status})))

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
                            fields)]
       (importer/import-collection importer user coll-id type-field)
       (response {:status (importer/status importer)})))))

(defn datasets-endpoint
  [service]
  (context "/api/datasets" []
    (restrict (datasets-routes service) {:handler authenticated?})))
