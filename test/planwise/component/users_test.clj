(ns planwise.component.users-test
  (:require [clojure.test :refer :all]
            [planwise.boundary.users :as users]
            [planwise.test-system :as test-system]
            [integrant.core :as ig]
            [clj-time.core :as time]
            [clojure.java.jdbc :as jdbc]))

(def test-user-id   1)
(def test-email     "user@example.com")
(def test-full-name "John Doe")
(def test-scope     "resourcemap.instedd.org")

(defn fixture-data
  []
  [[:users
    [{:id         test-user-id
      :email      test-email
      :full_name  test-full-name
      :created_at (time/now)}]]
   [:tokens
    [{:user_id       test-user-id
      :scope         test-scope
      :token         "EXPIRED-TOKEN-1"
      :refresh_token "REFRESH-TOKEN-1"
      :expires       (time/ago (time/minutes 5))}
     {:user_id       test-user-id
      :scope         test-scope
      :token         "TOKEN-2"
      :refresh_token "REFRESH-TOKEN-2"
      :expires       (time/ago (time/minutes 1))}]]])

(defn test-config
  []
  (test-system/config
   {:planwise.test/fixtures   {:fixtures (fixture-data)}
    :planwise.component/users {:db (ig/ref :duct.database/sql)}}))

(deftest find-user-test
  (test-system/with-system (test-config)
    (let [store (:planwise.component/users system)
          user (users/find-user store test-user-id)]
      (is (some? user))
      (is (= test-user-id (:id user)))
      (is (= test-email (:email user)))
      (is (= test-full-name (:full-name user)))
      (is (nil? (:last-login user))))))

(deftest find-user-or-create-test
  (test-system/with-system (test-config)
    (let [store (:planwise.component/users system)]
      (let [existing (users/find-or-create-user-by-email store test-email)]
        (is (some? existing))
        (is (= test-user-id (:id existing)))
        (is (= test-full-name (:full-name existing))))
      (let [_ (test-system/execute-sql system "ALTER SEQUENCE users_id_seq RESTART WITH 2")
            new-user (users/find-or-create-user-by-email store "other@instedd.org")]
        (is (some? new-user))
        (is (= 2 (:id new-user)))
        (is (= "other@instedd.org" (:email new-user)))
        (is (nil? (:last-login new-user)))))))

;; depends on clock-synchronization between host and database
;; change it so now is read from the database, or to update the database with a
;; given timestamp
#_(deftest update-last-login-test
  (test-system/with-system (test-config)
    (let [store (:planwise.component/users system)
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
  (test-system/with-system (test-config)
    (let [store (:planwise.component/users system)
          email test-email
          token (users/find-latest-token-for-scope store test-scope email)]
      (is (= "TOKEN-2" (:token token)))
      (is (= "REFRESH-TOKEN-2" (:refresh-token token)))
      (is (time/after? (:expires token) (time/ago (time/minutes 2)))))))

(deftest save-token-test
  (test-system/with-system (test-config)
    (let [store (:planwise.component/users system)
          scope test-scope
          new-token {:token "SAVE-TOKEN"
                     :refresh-token "SAVE-REFRESH"
                     :expires (time/from-now (time/minutes 5))}
          _ (users/save-token-for-scope! store test-scope test-email new-token)
          token (users/find-latest-token-for-scope store test-scope test-email)]
      (is (= new-token token)))))
