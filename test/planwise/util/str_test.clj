(ns planwise.util.str-test
  (:require [clojure.test :refer :all]
            [planwise.util.str :as str]))

(deftest extract-name-and-copy-number
  (is (= {:name "foo" :copy 0} (str/extract-name-and-copy-number "foo")))
  (is (= {:name "bar" :copy 1} (str/extract-name-and-copy-number "bar copy")))
  (is (= {:name "baz" :copy 45} (str/extract-name-and-copy-number "baz copy 45"))))

(deftest next-name
  (is (= "foo copy" (str/next-name ["foo"])))
  (is (= "bar copy 2" (str/next-name ["bar copy"])))
  (is (= "baz copy 8" (str/next-name ["baz copy 3" "baz copy 7"])))
  (is (= "qux copy 2" (str/next-name ["qux copy" "qux fux 2"]))))
