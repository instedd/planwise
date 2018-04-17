(ns planwise.boundary.projects2)

(defprotocol Projects2
  "API for manipulating projects"

  (create-project [this owner-id]
    "Creates a project. Returns a map with the database generated id.")

  (list-projects [this owner-id]
    "Returns projects accessible by the user.")

  (get-project [this project-id]
    "Returns project with the given ID")

  (update-project [this project]
    "Updates project's attributes. Returns the updated project.")

  (start-project [this project-id]
    "Starts project.")

  (delete-project [this project-id]
    "Registers when project is deleted.")

  (reset-project [this project-id]
    "Resets project to draft."))
