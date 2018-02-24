(ns planwise.component.users
  (:require [planwise.boundary.users :as boundary]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clj-time.jdbc]
            [hugsql.core :as hugsql]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/users.sql")


;; ----------------------------------------------------------------------
;; Component definition

(defrecord UsersStore [db]

  boundary/Users
  (find-user [{:keys [db]} user-id]
    (select-user (:spec db) {:id user-id}))
  (find-or-create-user-by-email [{:keys [db]} email]
    (jdbc/with-db-transaction [tx (:spec db)]
      (let [user (select-user-by-email tx {:email email})]
        (if-not user
          (let [user-id (-> (create-user! tx {:email email, :full-name nil})
                            (first)
                            (:id))]
            (select-user tx {:id user-id}))
          user))))
  (update-user-last-login! [{:keys [db]} user-id]
    (let [affected-rows (update-last-login! (:spec db) {:id user-id})]
      (if (= affected-rows 1)
        true
        (do
          (error "Update last login for non-existent user for ID " user-id)
          false))))
  (save-token-for-scope! [{:keys [db]} scope email token]
    (save-user-token! (:spec db) {:email email
                                  :scope scope
                                  :token (:token token)
                                  :refresh-token (:refresh-token token)
                                  :expires (:expires token)}))
  (find-latest-token-for-scope [{:keys [db]} scope email]
    (let [token (find-latest-user-token (:spec db) {:email email :scope scope})]
      (when token
        (select-keys token [:expires :refresh-token :token])))))


(defmethod ig/init-key :planwise.component/users
  [_ config]
  (map->UsersStore config))
