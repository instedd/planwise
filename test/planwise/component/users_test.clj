(ns planwise.component.users-test
  (:require [clojure.test :refer :all]
            [clj-time.core :as time]
            [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [planwise.boundary.users :as users]
            [planwise.test-system :refer [test-system with-system]]))

(defn execute-sql
  [system sql]
  (jdbc/execute! (get-in system [:db :spec]) sql))

(def test-email "user@example.com")
(def test-full-name "John Doe")
(def test-user-id 1)
(def test-scope "resourcemap.instedd.org")

(defn fixture-data
  []
  [[:users
    [{:id test-user-id :email test-email :full_name test-full-name
      :created_at (time/now)}]]
   [:tokens
    [{:user_id test-user-id :scope test-scope :token "EXPIRED-TOKEN-1"
      :refresh_token "REFRESH-TOKEN-1" :expires (time/ago (time/minutes 5))}
     {:user_id test-user-id :scope test-scope :token "TOKEN-2"
      :refresh_token "REFRESH-TOKEN-2" :expires (time/ago (time/minutes 1))}]]])

(defn system
  []
  (into
   (test-system {:fixtures {:data (fixture-data)}})
   {:users-store (component/using nil #_(users/users-store) [:db])}))

(deftest find-user-test
  (with-system (system)
    (let [store (:users-store system)
          user (users/find-user store test-user-id)]
      (is (some? user))
      (is (= test-user-id (:id user)))
      (is (= test-email (:email user)))
      (is (= test-full-name (:full-name user)))
      (is (nil? (:last-login user))))))

(deftest find-user-or-create-test
  (with-system (system)
    (let [store (:users-store system)]
      (let [existing (users/find-or-create-user-by-email store test-email)]
        (is (some? existing))
        (is (= test-user-id (:id existing)))
        (is (= test-full-name (:full-name existing))))
      (let [_ (execute-sql system "ALTER SEQUENCE users_id_seq RESTART WITH 2")
            new-user (users/find-or-create-user-by-email store "other@instedd.org")]
        (is (some? new-user))
        (is (= 2 (:id new-user)))
        (is (= "other@instedd.org" (:email new-user)))
        (is (nil? (:last-login new-user)))))))

;; depends on clock-synchronization between host and database
;; change it so now is read from the database, or to update the database with a given timestamp
#_(deftest update-last-login-test
  (with-system (system)
    (let [store (:users-store system)
          start-time (time/now)
          user-before (users/find-user store test-user-id)
          result (users/update-user-last-login! store test-user-id)
          user-after (users/find-user store test-user-id)]
      (is (nil? (:last-login user-before)))
      (is (true? result))
      (is (some? (:last-login user-after)))
      (is (time/after? (:last-login user-after) start-time))
      (is (time/before? (:last-login user-after) (time/now))))))

(deftest find-latest-token-test
  (with-system (system)
    (let [store (:users-store system)
          email test-email
          token (users/find-latest-token-for-scope store test-scope email)]
      (is (= "TOKEN-2" (:token token)))
      (is (= "REFRESH-TOKEN-2" (:refresh-token token)))
      (is (time/after? (:expires token) (time/ago (time/minutes 2)))))))

(deftest save-token-test
  (with-system (system)
    (let [store (:users-store system)
          scope test-scope
          new-token {:token "SAVE-TOKEN"
                     :refresh-token "SAVE-REFRESH"
                     :expires (time/from-now (time/minutes 5))}
          _ (users/save-token-for-scope! store test-scope test-email new-token)
          token (users/find-latest-token-for-scope store test-scope test-email)]
      (is (= new-token token)))))
