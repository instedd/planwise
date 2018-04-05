(ns planwise.client.scenarios.api)

(defn- load-scenario
  [id]
  {:method    :get
   :params     {:id id}
   :uri       (str "/api/scenarios" id)})