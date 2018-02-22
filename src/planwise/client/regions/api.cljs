(ns planwise.client.regions.api
  (:require [clojure.string :as str]))

(def load-regions
  {:method :get
   :uri    "/api/regions"})

(defn load-regions-with-preview
  [ids]
  {:method :get
   :uri    (str "/api/regions/" (str/join "," ids) "/with-preview")})

(defn load-regions-with-geo
  [ids]
  {:method :get
   :uri    (str "/api/regions/" (str/join "," ids) "/with-geo")})
