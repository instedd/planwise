(ns planwise.client.scenarios.api)

(defn- load-scenario
  [id]
  {:method    :get
   :uri       (str "/api/scenarios/" id)})

(defn- copy-scenario
  [id]
  {:method    :post
   :uri       (str "/api/scenarios/" id "/copy")})

(defn- update-scenario
  [id scenario]
  {:method    :put
   :params    {:scenario scenario}
   :uri       (str "/api/scenarios/" id)})
