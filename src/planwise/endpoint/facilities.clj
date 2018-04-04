(ns planwise.endpoint.facilities
  (:require [planwise.boundary.facilities :as facilities]
            [planwise.boundary.maps :as maps]
            [compojure.core :refer :all]
            [integrant.core :as ig]
            [clojure.string :as string]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response]]))

(defn- facilities-criteria [{:keys [type region bbox excluding]}]
  {:region (some-> region Integer.)
   :types (when type
            (map
             #(Integer. %)
             (if (map? type)
               (vals type)
               type)))
   :bbox (when bbox
           (map
            #(Float. %)
            (string/split bbox #",")))
   :excluding (when-not (string/blank? excluding)
                (map
                 #(Integer. %)
                 (string/split excluding #",")))})

(defn- isochrone-criteria [{:keys [threshold algorithm simplify]}]
  {:threshold (some-> threshold Integer.)
   :algorithm algorithm
   :simplify (some-> simplify str Float.)})

(defn- endpoint-routes [service maps-service]
  (routes
   (GET "/" [dataset-id & params]
     (let [dataset-id (Integer. dataset-id)
           criteria   (facilities-criteria params)
           facilities (facilities/list-facilities service dataset-id criteria)]
       (response {:count (count facilities)
                  :facilities facilities})))

   (GET "/types" [dataset-id]
     (let [dataset-id (Integer. dataset-id)
           types      (facilities/list-types service dataset-id)]
       (response (map (fn [type]
                        {:value (:id type)
                         :label (:name type)})
                      types))))

   (ANY "/bbox-isochrones" [dataset-id & params]
     (let [dataset-id (Integer. dataset-id)
           criteria   (facilities-criteria params)
           isochrone  (isochrone-criteria params)
           facilities (facilities/isochrones-in-bbox service dataset-id isochrone criteria)]
       (response {:facilities facilities})))))

(defn facilities-endpoint [{service :facilities, maps :maps}]
  (context "/api/facilities" []
    (restrict (endpoint-routes service maps) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/facilities
  [_ config]
  (facilities-endpoint config))
