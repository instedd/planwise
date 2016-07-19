(ns planwise.client.regions.api
  (:require [ajax.core :refer [GET POST]]
            [clojure.string :as str]
            [planwise.client.api :refer [json-request]]))

(defn load-regions-with-geo [ids & handlers]
  (if-not (empty? ids)
    (GET
      (str "/api/regions/" (str/join "," ids) "/with-geo")
      (json-request {} handlers))))

(defn load-regions [& handlers]
  (GET
    "/api/regions/"
    (json-request {} handlers)))
