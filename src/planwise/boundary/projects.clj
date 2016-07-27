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

  (delete-project [this id]
    "Deletes a project with the given ID. Returns true iff there was a row
    affected."))

;; Reference implementation

(extend-protocol Projects
  planwise.component.projects.ProjectsService
  (create-project [service project]
    (service/create-project service project))
  (list-projects [service]
    (service/list-projects service))
  (get-project [service id]
    (service/get-project service id))
  (delete-project [service id]
    (service/delete-project service id)))
