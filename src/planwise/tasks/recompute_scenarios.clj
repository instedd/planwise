(ns planwise.tasks.recompute-scenarios
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [duct.core :as duct]
            [integrant.core :as ig]
            [planwise.config]
            [planwise.boundary.engine :as engine]
            [planwise.component.scenarios :as scenarios]
            [taoensso.timbre :as timbre]
            [planwise.boundary.projects2 :as p2]))

(timbre/refer-timbre)
(duct/load-hierarchy)


(defn recompute-scenario
  [project store {:keys [id]}]
  (scenarios/compute-scenario id {:store store :project project}))

(defn recompute-scenarios
  [projects2 store [project-id scenarios]]
  (let [project    (p2/get-project projects2 project-id)]
    (dorun (pmap (partial recompute-scenario project store) scenarios))))

(defn recompute-scenario-groups
  [groups store projects2]
  (dorun (pmap (partial recompute-scenarios projects2 store) groups)))

(defn recompute-initial-scenario
  [projects2 store {:keys [id project-id] :as scenario}]
  (let [project    (p2/get-project projects2 project-id)]
    (scenarios/compute-initial-scenario id {:store store :project project})))


(defn recompute-initial-scenarios
  [initial-scenarios store projects2]
  (dorun (map (partial recompute-initial-scenario projects2 store) initial-scenarios)))


(defn -main [& args]
  ;; Logging configuration for production
  (timbre/merge-config! {:level        :info
                         :ns-blacklist ["com.zaxxer.hikari.*"
                                        "org.apache.http.*"
                                        "org.eclipse.jetty.*"]})
  (let [config    (-> (io/resource "planwise/cli.edn")
                      duct/read-config
                      duct/prep)
        _         (println "Starting system")
        system    (ig/init config)]

    (try
      (let [projects2               (:planwise.component/projects2 system)
            store                   (:planwise.component/scenarios system)
            database                (:spec (second (ig/find-derived-1 system :duct.database/sql)))
            project                 (p2/get-project projects2 11)
            initial-scenarios       (jdbc/query database ["SELECT id, label, \"project-id\" FROM scenarios WHERE label = 'initial'"])
            scenarios               (jdbc/query database ["SELECT id, label, \"project-id\" FROM scenarios WHERE label <> 'initial'"])
            scenarios               (group-by :project-id scenarios)]

        (recompute-initial-scenarios initial-scenarios store projects2)
        (recompute-scenario-groups scenarios store projects2))

      (finally
        (println "Shutting down system")
        (ig/halt! system)))))
