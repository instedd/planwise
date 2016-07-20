(ns planwise.endpoint.datasets
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as timbre]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response]]
            [clojure.set :refer [rename-keys]]
            [planwise.boundary.facilities :as facilities]
            [planwise.component.facilities :as facilities-component]
            [planwise.component.resmap :as resmap]))

(timbre/refer-timbre)

(defn- usable-field?
  [field]
  (#{"select_one"} (:kind field)))

(defn sites-with-location [sites]
  (filter #(and (:lat %) (:long %)) sites))

(defn facility-type-ctor [type-field]
  (let [field-name (:code type-field)
        type-path [:properties (keyword field-name)]]
    (fn [site]
      (let [f_type (get-in site type-path)]
        ;; FIXME: perform mapping to the facilities_types table
        f_type))))

(defn site->facility-ctor [type-field]
  (let [facility-type (facility-type-ctor type-field)]
    (fn [site]
      (-> site
          (select-keys [:id :name :lat :long])
          (rename-keys {:long :lon})
          (assoc :type (facility-type site))))))

(defn sites->facilities [sites type-field]
  (->> sites
       (sites-with-location)
       (map (site->facility-ctor type-field))))

(defn import-collection
  [resmap facilities user coll-id type-field]
  (info "Destroying existing facilities")
  (facilities-component/destroy-facilities! facilities)
  (loop [page 1]
    (let [data (resmap/get-collection-sites resmap user coll-id {:page page})
          sites (:sites data)]
      (when (seq sites)
        (info "Processing page" page "of collection" coll-id)
        (let [new-facilities (sites->facilities sites type-field)]
          (info "Inserting" (count new-facilities) "facilities")
          (facilities-component/insert-facilities! facilities new-facilities))
        (recur (inc page)))))
  (info "Done importing facilities from collection" coll-id))

(defn- datasets-routes
  [{:keys [facilities resmap]}]
  (routes
   (GET "/info" request
     (let [user (:identity request)
           facility-count (facilities/count-facilities facilities)
           authorised? (resmap/authorised? resmap user)
           collections (when authorised?
                         (resmap/list-user-collections resmap user))]
       (response {:authorised? authorised?
                  :facility-count facility-count
                  :collections collections})))

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

       (import-collection resmap facilities user coll-id type-field)

       (response {:status :ok})))))

(defn datasets-endpoint
  [services]
  (context "/api/datasets" []
    (restrict (datasets-routes services) {:handler authenticated?})))
