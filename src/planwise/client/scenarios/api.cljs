(ns planwise.client.scenarios.api)

(defn- load-scenario
  [id]
  {:method    :get
   :uri       (str "/api/scenarios/" id)})
