(ns planwise.boundary.users)

(defprotocol Users
  (find-user [store user-id]
    "Retrieves a single user by ID")

  (find-or-create-user-by-email [store email])
  (update-user-last-login! [store user-id])
  (save-token-for-scope! [store scope email token])
  (find-latest-token-for-scope [store scope email]))
