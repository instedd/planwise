(ns planwise.client.utils-test
  (:require [cljs.test :as t :refer-macros [deftest is]]
            [clojure.string :as str]
            [planwise.client.utils :as sut]))


(deftest pluralize-test
  (is (= "1 site" (sut/pluralize 1 "site")))
  (is (= "2 sites" (sut/pluralize 2 "site")))
  (is (= "1 person" (sut/pluralize 1 "person" "people")))
  (is (= "2 people" (sut/pluralize 2 "person" "people"))))

(deftest update-by-id-test
  (let [coll [{:id 1 :name "foo"}
              {:id 2 :name "bar"}
              {:id 3 :name "quux"}]]
    (is (= [{:id 1 :name "foo"}
            {:id 2 :name "BAR"}
            {:id 3 :name "quux"}]
           (sut/update-by-id coll 2 update :name str/upper-case)))))
