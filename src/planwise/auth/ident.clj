(ns planwise.auth.ident)

;; User identity related functions
;; The user identity is the user information carried around in the session
;; cookies and the JWE tokens.

(defn user->ident
  [user]
  {:user-email (:email user)
   :user-id    (:id user)})

(defn user-email
  [user-ident]
  (:user-email user-ident))

(defn user-id
  [user-ident]
  (:user-id user-ident))
