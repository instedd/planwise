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

(defn- load-scenarios
  [id]
  {:method    :get
   :uri       (str "/api/projects2/" id "/scenarios")})

(defn- suggest-providers
  [id]
  {:method    :get
   :timeout   60000
   :uri       (str "/api/scenarios/" id "/new-provider")})
