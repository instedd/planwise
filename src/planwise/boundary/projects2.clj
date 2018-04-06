(ns planwise.boundary.projects2)

(defprotocol Projects2
  "API for manipulating projects"

  (create-project [this user-id]
    "Creates a project. Returns a map with the database generated id.")

  (list-projects [this user-id]
    "Returns projects accessible by the user.")

  (get-project [this project-id]
    "Returns project with the given ID")

  (update-project [this project]
    "Updates project's attributes. Returns the updated project.")

  (start-project [this project-id]
    "Set project's state started "))
