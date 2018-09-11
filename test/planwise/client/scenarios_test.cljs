(ns planwise.client.scenarios-test
  (:require [clojure.string :as str]
            [planwise.client.scenarios.subs :as sut]
            [cljs.test :as t :refer-macros [deftest is]]))

(deftest new-provider-from-change-test
  (let [change {:provider-id "foo"
                :action      "create-provider"
                :location    {:lat 0 :lon 0}}]
    (is (= {:id             "foo"
            :name           "New Provider 1"
            :matches-filter true
            :location       {:lat 0 :lon 0}
            :change         change}
           (sut/new-provider-from-change change 1)))))

(deftest update-by-id-test
  (let [coll [{:id 1 :name "foo"}
              {:id 2 :name "bar"}
              {:id 3 :name "quux"}]]
    (is (= [{:id 1 :name "foo"}
            {:id 2 :name "BAR"}
            {:id 3 :name "quux"}]
           (sut/update-by-id coll 2 #(update % :name str/upper-case))))))
