(ns planwise.boundary.auth)

(defprotocol Auth
  (find-auth-token [service scope user-ident])
  (create-jwe-token [service user-ident]))
