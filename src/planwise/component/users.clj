(ns planwise.component.users
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.java.jdbc :as jdbc]
            [clj-time.jdbc]
            [hugsql.core :as hugsql]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/users.sql")

(defn get-db
  "Retrieve the database connection for a service"
  [component]
  (get-in component [:db :spec]))


;; ----------------------------------------------------------------------
;; Component definition

(defrecord UsersStore [db])

(defn users-store
  "Construct a Users store component"
  []
  (map->UsersStore {}))


(defn find-user
  "Retrieves a single user by ID"
  [service user-id]
  (select-user (get-db service) {:id user-id}))

(defn find-or-create-user-by-email
  "Finds a user by email, creating it if it doesn't exist"
  [service email]
  (jdbc/with-db-transaction [tx (get-db service)]
    (let [user (select-user-by-email tx {:email email})]
      (if-not user
        (let [user-id (-> (create-user! tx {:email email, :full_name nil})
                          (first)
                          (:id))]
          (select-user tx {:id user-id}))
        user))))

(defn update-user-last-login!
  "Updates the last login time for a given user ID"
  [service user-id]
  (let [affected-rows (update-last-login! (get-db service) {:id user-id})]
    (if (= affected-rows 1)
      true
      (do
        (error "Update last login for non-existent user for ID " user-id)
        false))))

(defn find-latest-token-for-scope
  [service scope email]
  (let [token (find-latest-user-token (get-db service) {:email email :scope scope})]
    (when token
      {:expires (:expires token)
       :refresh-token (:refresh_token token)
       :token (:token token)})))

(defn save-token-for-scope!
  [service scope email token]
  (save-user-token! (get-db service) {:email email
                                      :scope scope
                                      :token (:token token)
                                      :refresh-token (:refresh-token token)
                                      :expires (:expires token)}))
