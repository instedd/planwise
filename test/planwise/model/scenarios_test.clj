(ns planwise.model.scenarios-test
  (:require [planwise.model.scenarios :as sut]
            [clojure.test :refer :all]))


(deftest test-next-name-from-initial
  (is (= "Scenario A" (sut/next-name-from-initial ["Initial"])))
  (is (= "Scenario A" (sut/next-name-from-initial ["Initial" "Scenario" "Other Name"])))
  (is (= "Scenario A" (sut/next-name-from-initial ["Initial" "Scenario 1"])))
  (is (= "Scenario B" (sut/next-name-from-initial ["Initial" "Scenario A"])))
  (is (= "Scenario C" (sut/next-name-from-initial ["Initial" "Scenario B"])))
  (is (= "Scenario Z" (sut/next-name-from-initial ["Initial" "Scenario Y"])))
  (is (= "Scenario 1" (sut/next-name-from-initial ["Initial" "Scenario Z"])))
  (is (= "Scenario 2" (sut/next-name-from-initial ["Initial" "Scenario Z" "Scenario 1"])))
  (is (= "Scenario 43" (sut/next-name-from-initial ["Initial" "Scenario Z" "Scenario 42"]))))

(deftest test-next-name-for-copy
  (is (= "Foo (1)" (sut/next-name-for-copy ["Foo"] "Foo")))
  (is (= "Foo (1)" (sut/next-name-for-copy ["Foo" "Bar (1)"] "Foo")))
  (is (= "Foo (1)" (sut/next-name-for-copy ["Foo" "Foo2 (1)"] "Foo")))
  (is (= "Foo (2)" (sut/next-name-for-copy ["Foo" "Foo (1)"] "Foo")))
  (is (= "Foo (2)" (sut/next-name-for-copy ["Foo" "Foo(1)"] "Foo")))
  (is (= "Foo (2)" (sut/next-name-for-copy ["Foo" "Foo (1)"] "Foo (1)")))
  (is (= "Foo (3)" (sut/next-name-for-copy ["Foo (2)"] "Foo"))))
