(ns planwise.component.importer-test
  (:require  [planwise.component.importer :as importer]
             [clojure.test :refer :all]))


(deftest facility-type-ctor-test
  (let [type-field {:code "facility_type"
                    :options {1 10,
                              2 11}}

        site       {:id 1, :name "s1",
                    :lat -1.28721692771827, :long 36.8136030697266,
                    :properties {:facility_type 1}}

        facility-type (importer/facility-type-ctor type-field)]

    (is (= 10 (facility-type site)))))

(deftest sites->facilities-test
  (let [type-field          {:code "facility_type",
                             :options {1 10,
                                       2 11}}

        sites               [{:id 1, :name "s1",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {:facility_type 1}}

                             ;; sites without location are ignored
                             {:id 2, :name "s2",
                              :properties {:facility_type 1}}

                             ;; sites without a type are ignored
                             {:id 3, :name "s3",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {}}
                             {:id 4, :name "s4",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {:facility_type nil}}]

        facility-type       (importer/facility-type-ctor type-field)
        facilities          (importer/sites->facilities sites facility-type)

        expected-facilities [{:lat -1.28721692771827,
                              :lon 36.8136030697266,
                              :name "s1",
                              :site-id 1,
                              :type-id 10}]]

    (is (= expected-facilities facilities))))
