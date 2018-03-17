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
   :params    {:id project-id}
   :uri       (str "/api/projects2/" project-id)})
