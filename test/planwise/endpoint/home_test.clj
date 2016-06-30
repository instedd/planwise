(ns planwise.endpoint.home-test
  (:require [planwise.endpoint.home :as home]
            [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]))

(def handler
  (home/home-endpoint {}))

(def home-paths ["/"
                 "/playground"
                 "/projects/1"
                 "/projects/1/facilities"
                 "/projects/1/transport"
                 "/projects/1/scenarios"])

(deftest root-page-test
  (doseq [path home-paths]
    (testing (str "path " path " exists and renders CLJS application")
     (-> (session handler)
         (visit path)
         (has (status? 200))
         (has (some-text? "Loading Application"))))))
