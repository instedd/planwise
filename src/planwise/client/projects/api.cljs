(ns planwise.client.projects.api)

(def load-projects
  {:method :get
   :uri    "/api/projects"})

(def create-project
  {:method :post
   :uri    "/api/projects/"})

(defn delete-project [id]
  {:method :delete
   :uri    (str "/api/projects/" id)})

(defn leave-project [id]
  {:method :delete
   :uri    (str "/api/projects/" id "/access")})
