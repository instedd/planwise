(ns planwise.client.scenarios.api)

(defn- load-scenario
  [id]
  {:method    :get
   :uri       (str "/api/scenarios/" id)})

(defn- create-scenario
  [current-scenario]
  {:method    :post
   :params    {:current-scenario current-scenario}
   :uri       (str "/api/scenarios/" (:id current-scenario))})
