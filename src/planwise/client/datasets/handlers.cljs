(ns planwise.client.datasets.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [planwise.client.datasets.api :as api]))

(def in-datasets (path [:datasets]))

(register-handler
 :datasets/initialise!
 in-datasets
 (fn [db [_]]
   (when-not (:initialised? db)
     (api/load-datasets-info :datasets/info-loaded))
   db))

(register-handler
 :datasets/reload-info
 in-datasets
 (fn [db [_]]
   (c/log "Reloading datasets information")
   (api/load-datasets-info :datasets/info-loaded)
   db))

(register-handler
 :datasets/info-loaded
 in-datasets
 (fn [db [_ datasets-info]]
   (-> db
       (assoc-in [:resourcemap :authorised?] (:authorised? datasets-info))
       (assoc-in [:resourcemap :collections] (:collections datasets-info))
       (assoc :initialised? true
              :facility-count (:facility-count datasets-info)))))
