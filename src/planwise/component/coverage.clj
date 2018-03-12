(ns planwise.component.coverage
  (:require [planwise.boundary.coverage :as boundary]
            [planwise.component.coverage.pgrouting :as pgrouting]
            [integrant.core :as ig]))


(def supported-algorithms
  {:pgrouting-alpha
   {:label       "Travel by car"
    :description "Computes all reachable OSM nodes from the nearest to the
                  starting point and then applies the alpha shape algorithm to
                  the resulting points"
    :criteria    {:driving-time {:label   "Driving time"
                                 :type    :enum
                                 :options [{:value 30  :label "30 minutes"}
                                           {:value 60  :label "1 hour"}
                                           {:value 90  :label "1:30 hours"}
                                           {:value 120 :label "2 hours"}]}}}})

(defmulti compute-coverage (fn [service point criteria] (:algorithm criteria)))

(defmethod compute-coverage :default
  [service point criteria]
  (throw (IllegalArgumentException. "Missing or invalid coverage algorithm")))

(defmethod compute-coverage :pgrouting-alpha
  [{:keys [db]} point criteria]
  (let [db-spec   (:spec db)
        pg-point  (pgrouting/make-point point)
        threshold (:driving-time criteria)
        result    (pgrouting/compute-coverage db-spec pg-point threshold)]
    (case (:result result)
      "ok" (:polygon result)
      (throw (RuntimeException. (str "pgRouting coverage computation failed: " (:result result)))))))

(defrecord CoverageService [db]
  boundary/CoverageService
  (supported-algorithms [this]
    supported-algorithms)
  (compute-coverage [this point criteria]
    (compute-coverage this point criteria)))


(defmethod ig/init-key :planwise.component/coverage
  [_ config]
  (map->CoverageService config))


;; REPL testing

(comment

  (def service (second (ig/find-derived-1 integrant.repl.state/system :planwise.component/coverage)))

  (boundary/supported-algorithms service)

  (boundary/compute-coverage service
                             {:lat -3.0361 :lon 40.1333}
                             {:algorithm :pgrouting-alpha
                              :driving-time 60}))
