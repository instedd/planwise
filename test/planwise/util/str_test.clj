(ns planwise.util.str-test
  (:require [clojure.test :refer :all]
            [planwise.util.str :as str]))

(deftest next-alpha-name
  (is (= "A" (str/next-alpha-name "")))
  (is (= "B" (str/next-alpha-name "A")))
  (is (= "CA" (str/next-alpha-name "BZ")))
  (is (= "CAA" (str/next-alpha-name "BZZ"))))

(deftest extract-name-and-copy-number
  (is (= {:name "foo" :copy 0} (str/extract-name-and-copy-number "foo")))
  (is (= {:name "bar" :copy 1} (str/extract-name-and-copy-number "bar1")))
  (is (= {:name "" :copy 0} (str/extract-name-and-copy-number ""))))

(deftest next-name
  (is (= "foo1" (str/next-name ["foo"])))
  (is (= "bar2" (str/next-name ["bar1"])))
  (is (= "baz8" (str/next-name ["baz3" "baz7"])))
  (is (= "qux2" (str/next-name ["qux1" "qux fux2"]))))
