(ns planwise.client.scenarios-test
  (:require [planwise.client.scenarios.subs :as sut]
            [cljs.test :as t :refer-macros [deftest is]]))

(deftest new-provider-from-change-test
  (let [change {:id       "foo"
                :action   "create-provider"
                :name     "sample provider"
                :location {:lat 0 :lon 0}}]
    (is (= {:id             "foo"
            :name           "sample provider"
            :matches-filter true
            :location       {:lat 0 :lon 0}
            :change         change}
           (sut/new-provider-from-change change 1)))))

