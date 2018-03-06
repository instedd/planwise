(ns planwise.boundary.projects)

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
    affected.")

  (create-project-share [this project-id token user-id]
    "If the token is valid for the selected project, upserts a new project share
     for the specified user id, and returns the project. Returns nil otherwise.")

  (delete-project-share [this project-id user-id]
    "Deletes a project share given the project and grantee ids. Returs true iff
     there was a share deleted.")

  (list-project-shares [this project-id]
    "List all project shares for the specified project.")

  (reset-share-token [this project-id]
    "Updates the project's share token and returns the new value, or nil if no
     project was found for the specified id.")

  (share-via-email [this project-or-id emails opts]
    "Sends the project share token via email to the specified recipients.
     Required options are: `:host`.")

  (list-projects-for-dataset [this dataset-id])

  (update-project-stats [this project]))
