(ns planwise.endpoint.home-test
  (:require [planwise.endpoint.home :as home]
            [planwise.boundary.auth :as auth]
            [planwise.boundary.maps :as maps]
            [buddy.auth.middleware :refer [wrap-authorization]]
            [buddy.auth.backends :as backends]
            [buddy.core.nonce :as nonce]
            [clojure.test :refer :all]
            [integrant.core :as ig]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [schema.test]))

(use-fixtures :once schema.test/validate-schemas)

(def mock-auth-service
  (reify auth/Auth
    (create-jwe-token [service ident] "TOKEN")))

(def mock-maps-service
  (reify maps/Maps
    (mapserver-url [service] "http://mapserver")))

(def test-config
  {:planwise.endpoint/home
   {:auth mock-auth-service
    :maps mock-maps-service}})

(defn handler
  []
  (-> (ig/init test-config)
      :planwise.endpoint/home
      (wrap-authorization (backends/session))))

(def home-paths ["/providers"
                 "/sources"
                 "/projects2/1"
                 "/projects2/1/scenarios"
                 "/projects2/1/settings"
                 "/projects2/1/scenarios/1"])

(deftest home-endpoint-checks-login
  (let [handler (handler)]
    (doseq [path home-paths]
      (testing (str "path " path " throws 401")
        (-> (session handler)
            (visit path)
            (has (status? 401)))))))

(deftest root-page-test
  (let [handler (handler)]
    (doseq [path home-paths]
      (testing (str "path " path " exists and renders CLJS application")
        (-> (session handler)
            (visit path :identity {:user-id 1 :user-email "foo@example.com"})
            (has (status? 200))
            (has (some-text? "Loading Application")))))))

(deftest root-renders-landing-or-application
  (let [handler (handler)]
    (testing "root renders landing if not authenticated"
      (-> (session handler)
          (visit "/")
          (has (status? 200))
          (has (some-text? "What is Planwise?"))))
    (testing "root renders application if authenticated"
      (-> (session handler)
          (visit "/" :identity {:user-id 1 :user-email "foo@example.com"})
          (has (status? 200))
          (has (some-text? "Loading Application"))))))
