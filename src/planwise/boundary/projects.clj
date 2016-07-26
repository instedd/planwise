(ns planwise.boundary.projects
  (:require [planwise.component.projects :as service]))

(defprotocol Projects
  "API for managing projects."

  (create-project [this project]
    "Creates a project given a map of its attributes. Returns a map with the
    database generated id.")

  (list-projects [this]
    "Returns all projects in the database.")

  (get-project [this id]
    "Return project with the given ID.")

  (update-project [this project]
    "Updates a project's attributes. Returns the updated project on success and
    nil on failure."))

;; Reference implementation

(extend-protocol Projects
  planwise.component.projects.ProjectsService
  (create-project [service project]
    (service/create-project service project))
  (list-projects [service]
    (service/list-projects service))
  (get-project [service id]
    (service/get-project service id))
  (update-project [service project]
    (service/update-project service project)))
