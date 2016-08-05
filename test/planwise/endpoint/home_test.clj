(ns planwise.endpoint.home-test
  (:require [planwise.endpoint.home :as home]
            [buddy.core.nonce :as nonce]
            [buddy.auth.middleware :refer [wrap-authorization]]
            [buddy.auth.backends :as backends]
            [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [schema.test]))

(use-fixtures :once schema.test/validate-schemas)

(def mocked-auth-service
  {:jwe-secret (nonce/random-bytes 32)})

(def handler
  (-> (home/home-endpoint {:auth mocked-auth-service})
      (wrap-authorization (backends/session))))

(def home-paths ["/"
                 "/playground"
                 "/datasets"
                 "/projects/1"
                 "/projects/1/facilities"
                 "/projects/1/transport"
                 "/projects/1/scenarios"])

(deftest home-endpoint-checks-login
  (doseq [path home-paths]
    (testing (str "path " path " throws 401")
      (-> (session handler)
          (visit path)
          (has (status? 401))))))

(deftest root-page-test
  (doseq [path home-paths]
    (testing (str "path " path " exists and renders CLJS application")
      (-> (session handler)
          (visit path :identity {:user-id 1 :user-email "foo@example.com"})
          (has (status? 200))
          (has (some-text? "Loading Application"))))))
