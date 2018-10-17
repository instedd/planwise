(ns planwise.client.projects2.api)

;; ----------------------------------------------------------------------------
;; API methods

(defn- create-project!
  []
  {:method    :post
   :section   :index
   :uri       "/api/projects2"})

(defn- list-projects
  []
  {:method    :get
   :section   :index
   :uri       (str "/api/projects2")})

(defn- update-project
  [project-id project]
  {:method    :put
   :section   :show
   :params    {:project project}
   :uri       (str "/api/projects2/" project-id)})

(defn- get-project
  [project-id]
  {:method    :get
   :uri       (str "/api/projects2/" project-id)})

(defn- start-project!
  [project-id]
  {:method    :post
   :uri       (str "/api/projects2/" project-id "/start")})

(defn- reset-project!
  [project-id]
  {:method    :post
   :uri       (str "/api/projects2/" project-id "/reset")})

(defn- delete-project!
  [project-id]
  {:method    :delete
   :uri  (str "/api/projects2/" project-id)})

(defn- get-providers
  [project-id]
  {:method    :get
   :uri       (str "/api/projects2/" project-id "/providers")})
