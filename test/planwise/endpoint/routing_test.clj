(ns planwise.endpoint.routing-test
  (:require [planwise.endpoint.routing :as routing]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]))

(def mocked-routing-service
  (reify planwise.boundary.routing.Routing
    (nearest-node [service lat lon]
      {:id 123 :point "a point"})
    (compute-isochrone [service node-id distance algorithm]
      (if (some? algorithm)
        (str "isochrone from " node-id " with " distance " using " algorithm)
        (str "isochrone from " node-id " with " distance)))))

(def handler
  (wrap-params
   (routing/routing-endpoint {:routing mocked-routing-service})))

(defn json-body [state]
  (-> state
      (:response)
      (:body)
      (json/read-str)))

(deftest coerce-algorithm-test
  (is (nil?           (routing/coerce-algorithm nil)))
  (is (nil?           (routing/coerce-algorithm "")))
  (is (= :alpha-shape (routing/coerce-algorithm "alpha-shape")))
  (is (= :alpha-shape (routing/coerce-algorithm "ALPHA-SHAPE")))
  (is (= :buffer      (routing/coerce-algorithm "buffer")))
  (is (= :buffer      (routing/coerce-algorithm "Buffer")))
  (is (= :invalid     (routing/coerce-algorithm "foobar"))))

(deftest nearest-node-test
  (testing "with missing parameters"
    (-> (session handler)
        (visit "/routing/nearest-node")
        (has (status? 400)))
    (-> (session handler)
        (visit "/routing/nearest-node?lat=10")
        (has (status? 400)))
    (-> (session handler)
        (visit "/routing/nearest-node?lon=10")
        (has (status? 400))))

  (testing "with non-numeric parameters"
    (-> (session handler)
        (visit "/routing/nearest-node?lat=abc&lon=abc")
        (has (status? 400))))

  (testing "with both parameters"
    (let [body (-> (session handler)
                   (visit "/routing/nearest-node?lat=10&lon=10")
                   (has (status? 200))
                   (json-body))]
      (is (= {"id" 123 "point" "a point"} body)))))

(deftest isochrone-test
  (testing "with missing parameters"
    (-> (session handler)
        (visit "/routing/isochrone")
        (has (status? 400)))
    (-> (session handler)
        (visit "/routing/isochrone?node-id=123")
        (has (status? 400))))

  (testing "with invalid parameters"
    (-> (session handler)
        (visit "/routing/isochrone?node-id=123&threshold=600&algorithm=foo")
        (has (status? 400)))
    (-> (session handler)
        (visit "/routing/isochrone?node-id=123&threshold=abc&algorithm=buffer")
        (has (status? 400)))
    (-> (session handler)
        (visit "/routing/isochrone?node-id=abc&threshold=600&algorithm=buffer")
        (has (status? 400))))

  (testing "with valid parameters"
    (-> (session handler)
        (visit "/routing/isochrone?node-id=123&threshold=600")
        (has (status? 200))
        (has (text? "isochrone from 123 with 600.0")))
    (-> (session handler)
        (visit "/routing/isochrone?node-id=123&threshold=600&algorithm=buffer")
        (has (status? 200))
        (has (text? "isochrone from 123 with 600.0 using :buffer")))))
