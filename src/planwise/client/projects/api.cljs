(ns planwise.client.projects.api
  (:require [ajax.core :refer [GET POST]]
            [cljs.core.async :as async :refer [chan put! <!]]
            [planwise.client.api :refer [async-handlers]]))

(defn load-project [id]
  (let [c (chan)]
    (GET
      (str "/api/projects/" id)
      (assoc (async-handlers c identity)
            :format :json
            :response-format :json
            :keywords? true))
    c))


(defn create-project [params]
  (let [c (chan)]
    (POST "/api/projects/" (assoc (async-handlers c identity)
                              :format :json
                              :response-format :json
                              :keywords? true
                              :params params))
    c))
