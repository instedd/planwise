(ns planwise.boundary.projects
  (:require [planwise.component.projects :as service]))

(defprotocol Projects
  "API for managing projects."

  (create-project [this project]
    "Creates a project given a map of its attributes. Returns a map with the
    database generated id.")

  (list-projects-for-user [this user-id]
    "Returns projects accessible by the user.")

  (get-project
    [this id]
    [this id user-id]
    "Return project with the given ID, optionally filtered by access for the
     specified user id.")

  (update-project [this project]
    "Updates a project's attributes. Returns the updated project on success and
    nil on failure.")

  (delete-project [this id]
    "Deletes a project with the given ID. Returns true iff there was a row
    affected."))

;; Reference implementation

(extend-protocol Projects
  planwise.component.projects.ProjectsService
  (create-project [service project]
    (service/create-project service project))
  (list-projects-for-user [service user-id]
    (service/list-projects-for-user service user-id))
  (get-project
    ([service id]
     (service/get-project service id))
    ([service id user-id]
     (service/get-project service id user-id)))
  (update-project [service project]
    (service/update-project service project))
  (delete-project [service id]
    (service/delete-project service id)))

;; Additional utility functions

(defn owned-by?
  [project user-id]
  (service/owned-by? project user-id))
