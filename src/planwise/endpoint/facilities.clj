(ns planwise.endpoint.facilities
  (:require [planwise.boundary.facilities :as facilities]
            [planwise.boundary.maps :as maps]
            [compojure.core :refer :all]
            [clojure.string :as string]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response]]))

(defn- facilities-criteria [{:keys [type region bbox excluding]}]
  {:region (when region (Integer. region))
   :types (when type (map #(Integer. %) (if (map? type) (vals type) type)))
   :bbox (when bbox (map #(Float. %) (string/split bbox #",")))
   :excluding (when-not (string/blank? excluding) (map #(Integer. %) (string/split excluding #",")))})

(defn- isochrone-criteria [{:keys [threshold algorithm simplify]}]
  {:threshold (when threshold (Integer. threshold))
   :algorithm algorithm
   :simplify (when simplify (Float. (str simplify)))})

(defn- endpoint-routes [service maps-service]
  (routes
   (GET "/" [& params]
     (let [criteria   (facilities-criteria params)
           facilities (facilities/list-facilities service criteria)]
       (response {:count (count facilities)
                  :facilities facilities})))

   (GET "/types" req
     (let [types (facilities/list-types service)]
       (response (map (fn [type]
                        {:value (:id type)
                         :label (:name type)})
                      types))))

   (GET "/with-isochrones" [& params]
     (let [criteria   (facilities-criteria params)
           isochrone  (isochrone-criteria params)
           facilities (facilities/list-with-isochrones service isochrone criteria)
           region     (:region params)
           demand     (maps/demand-map maps-service region facilities)]
       (response
         (assoc demand
                :facilities facilities))))

   (ANY "/bbox-isochrones" [& params]
     (let [criteria   (facilities-criteria params)
           isochrone  (isochrone-criteria params)
           facilities (facilities/isochrones-in-bbox service isochrone criteria)]
       (response {:facilities facilities})))

   (GET "/isochrone" [threshold]
     (let [threshold (Integer. (or threshold 5400))
           isochrone (facilities/isochrone-all-facilities service threshold)]
       (response isochrone)))))

(defn facilities-endpoint [{service :facilities, maps :maps}]
  (context "/api/facilities" []
    (restrict (endpoint-routes service maps) {:handler authenticated?})))
