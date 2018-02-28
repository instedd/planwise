(ns planwise.endpoint.regions
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [ring.util.response :refer [content-type response not-found]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.boundary.regions :as regions]))

;; TODO consider storing a separate field with the name as provided by
;; OSM, or obtaining the name of the corresponding level-2 region.
(defn- add-country-name [region]
  (let [country-name (->> region
                          (:country)
                          (#(clojure.string/split % #"-"))
                          (map clojure.string/capitalize)
                          (clojure.string/join " "))]
    (assoc region :country-name country-name)))

(defn- endpoint-routes [service]
  (routes

   (GET "/" []
     (let [regions (regions/list-regions service)]
       (->> regions
            (map add-country-name)
            (response))))

   (GET "/:ids{[0-9\\,]+}/with-preview" [ids]
     (let [ids-array (map #(Integer/parseInt %) (str/split ids #","))
           regions (regions/list-regions-with-preview service ids-array)]
       (->> regions
            (map add-country-name)
            (response))))

   (GET "/:ids{[0-9\\,]+}/with-geo" [ids]
     (let [ids-array (map #(Integer/parseInt %) (str/split ids #","))
           regions (regions/list-regions-with-geo service ids-array 0.0)]
       (->> regions
            (map add-country-name)
            (response))))))

(defn regions-endpoint [{service :regions}]
  (context "/api/regions" []
           (restrict (endpoint-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/regions
  [_ config]
  (regions-endpoint config))
