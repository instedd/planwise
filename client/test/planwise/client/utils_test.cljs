(ns planwise.client.utils-test
  (:require [cljs.test :as t :refer-macros [deftest is]]
            [clojure.string :as str]
            [planwise.client.utils :as sut]
            [planwise.common :as common]))


(deftest pluralize-test
  (is (= "1 site" (common/pluralize 1 "site")))
  (is (= "2 sites" (common/pluralize 2 "site")))
  (is (= "1 person" (common/pluralize 1 "person" "people")))
  (is (= "2 people" (common/pluralize 2 "person" "people"))))

(deftest update-by-id-test
  (let [coll [{:id 1 :name "foo"}
              {:id 2 :name "bar"}
              {:id 3 :name "quux"}]]
    (is (= [{:id 1 :name "foo"}
            {:id 2 :name "BAR"}
            {:id 3 :name "quux"}]
           (sut/update-by-id coll 2 update :name str/upper-case)))))
