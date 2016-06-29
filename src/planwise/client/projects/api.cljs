(ns planwise.client.projects.api
  (:require [ajax.core :refer [GET POST]]
            [cljs.core.async :as async :refer [chan put! <!]]
            [planwise.client.api :refer [async-handlers]]))


(defn create-project [params]
  (let [c (chan)]
    (POST "/projects/" (assoc (async-handlers c identity)
                              :format :json
                              :response-format :json
                              :keywords? true
                              :params params))
    c))
