(ns planwise.client.utils-test
  (:require [cljs.test :as t :refer-macros [deftest is]]
            [planwise.client.utils :as sut]))


(deftest pluralize-test
  (is (= "1 site" (sut/pluralize 1 "site")))
  (is (= "2 sites" (sut/pluralize 2 "site")))
  (is (= "1 person" (sut/pluralize 1 "person" "people")))
  (is (= "2 people" (sut/pluralize 2 "person" "people"))))

