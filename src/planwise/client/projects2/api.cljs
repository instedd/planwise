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
  [project-id name]
  {:method    :put
   :section   :show
   :params    {:name name}
   :uri       (str "/api/projects2/" project-id)})

(defn- get-project
  [id]
  {:method    :get
   :params    {:id id}
   :uri       (str "/api/projects2/" id)})

