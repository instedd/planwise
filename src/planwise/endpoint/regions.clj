(ns planwise.endpoint.regions
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response not-found]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.boundary.regions :as regions]))

(defn- endpoint-routes [service]
  (routes
   (GET "/" []
     (let [regions (regions/list-regions service)]
       (response regions)))
   (GET "/:ids{[0-9\\,]+}/with-geo" [ids]
     (let [ids-array (map #(Integer/parseInt %) (str/split ids #","))
           regions (regions/list-regions-with-geo service ids-array 0.02)]
       (response regions)))))

(defn regions-endpoint [{service :regions}]
  (context "/api/regions" []
    (restrict (endpoint-routes service) {:handler authenticated?})))
