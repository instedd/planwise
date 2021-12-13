(ns planwise.client.scenarios.api)

(defn load-scenario
  [id]
  {:method    :get
   :uri       (str "/api/scenarios/" id)})

(defn copy-scenario
  [id]
  {:method    :post
   :uri       (str "/api/scenarios/" id "/copy")})

(defn update-scenario
  [id scenario]
  {:method    :put
   :params    {:scenario scenario}
   :uri       (str "/api/scenarios/" id)})

(defn load-scenarios
  [id]
  {:method    :get
   :uri       (str "/api/projects2/" id "/scenarios")})

(defn suggested-locations-for-new-provider
  [id]
  {:method    :get
   :timeout   90000
   :uri       (str "/api/scenarios/" id "/suggested-locations")})

(defn suggested-providers-to-improve
  [id]
  {:method    :get
   :timeout   90000
   :uri       (str "/api/scenarios/" id "/suggested-providers")})

(defn get-provider-geom
  [id provider-id]
  {:method :get
   :uri    (str "/api/scenarios/" id "/geometry/" provider-id)})

(defn get-suggestion-geom
  [id iteration]
  {:method :get
   :uri    (str "/api/scenarios/" id "/coverage/suggestion/" iteration)})

(defn- delete-scenario
  [id]
  {:method    :delete
   :uri  (str "/api/scenarios/" id)})

(defn download-scenario-sources
  [id]
  (str "/api/scenarios/" id "/sources"))

(defn download-scenario-providers
  [id]
  (str "/api/scenarios/" id "/providers"))
