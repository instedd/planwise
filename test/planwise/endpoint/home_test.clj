(ns planwise.endpoint.home-test
  (:require [planwise.endpoint.home :as home]
            [buddy.core.nonce :as nonce]
            [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]))

(defn mock-auth-service []
  {:jwe-secret (nonce/random-bytes 32)})

(def handler
  (home/home-endpoint {:auth (mock-auth-service)}))

(def home-paths ["/"
                 "/playground"
                 "/projects/1"
                 "/projects/1/facilities"
                 "/projects/1/transport"
                 "/projects/1/scenarios"])

(deftest home-endpoint-checks-login
  (doseq [path home-paths]
    (testing (str "path " path " throws 401")
      (is (thrown? clojure.lang.ExceptionInfo
                   (-> (session handler)
                       (visit path)))))))

(deftest root-page-test
  (doseq [path home-paths]
    (testing (str "path " path " exists and renders CLJS application")
      (-> (session handler)
          (visit path :identity {:user "foo@example.com"})
          (has (status? 200))
          (has (some-text? "Loading Application"))))))
