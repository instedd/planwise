(ns planwise.boundary.coverage-test
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]
            [planwise.boundary.coverage :as sut]))


(deftest enumerate-algorithm-options-test
  (let [service (stub sut/CoverageService)]
    (is (empty? (sut/enumerate-algorithm-options service :foo))
        "returns nil for invalid algorithms"))

  (let [service (stub sut/CoverageService
                      {:supported-algorithms {:foo {}}})]
    (is (empty? (sut/enumerate-algorithm-options service :foo))
        "returns nil for algorithms without options"))

  (let [service (stub sut/CoverageService
                      {:supported-algorithms
                       {:foo {:criteria {:bar {:type :enum
                                               :options [{:value 1}
                                                         {:value 2}
                                                         {:value 3}]}}}}})]
    (is (= [{:bar 1} {:bar 2} {:bar 3}] (sut/enumerate-algorithm-options service :foo))
        "returns the possible criterias for enum options"))

  (let [service (stub sut/CoverageService
                      {:supported-algorithms
                       {:foo {:criteria {:bar {:type :number}}}}})]
    (is (empty? (sut/enumerate-algorithm-options service :foo))
        "ignores non enum options"))

  (let [service (stub sut/CoverageService
                      {:supported-algorithms
                       {:foo {:criteria {:bar {:type :enum
                                               :options [{:value 1} {:value 2}]}
                                         :baz {:type :enum
                                               :options [{:value 3} {:value 4}]}}}}})]
    (is (= [{:bar 1 :baz 3} {:bar 1 :baz 4}
            {:bar 2 :baz 3} {:bar 2 :baz 4}]
           (sut/enumerate-algorithm-options service :foo))
        "returns the possible criteria combinations for multiple enum options")))
