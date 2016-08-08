(ns planwise.model.ident
  (:require [schema.core :as s]
            [planwise.model.users :refer [User]]))

(def Ident
  "User identity as found in the session cookie and JWE tokens"
  {:user-id              s/Int
   :user-email           s/Str

   ;; JWE tokens might include expiration information
   (s/optional-key :exp) s/Int})

;; User identity related functions
;; The user identity is the user information carried around in the session
;; cookies and the JWE tokens.

(s/defn user->ident :- Ident
  [user :- User]
  {:user-email (:email user)
   :user-id    (:id user)})

(s/defn user-email :- s/Str
  [user-ident :- Ident]
  (:user-email user-ident))

(s/defn user-id :- s/Int
  [user-ident :- Ident]
  (:user-id user-ident))
