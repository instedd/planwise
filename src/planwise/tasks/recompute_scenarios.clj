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
  [project scenarios-component {:keys [id]}]
  (scenarios/compute-scenario id {:store scenarios-component :project project}))

(defn recompute-scenarios
  [projects2 scenarios-component [project-id scenarios]]
  (let [project    (p2/get-project projects2 project-id)]
    (dorun (pmap (partial recompute-scenario project scenarios-component) scenarios))))

(defn recompute-scenario-groups
  [groups scenarios-component projects2]
  (dorun (pmap (partial recompute-scenarios projects2 scenarios-component) groups)))

(defn recompute-initial-scenario
  [projects2 scenarios-component {:keys [id project-id] :as scenario}]
  (let [project    (p2/get-project projects2 project-id)]
    (scenarios/compute-initial-scenario id {:store scenarios-component :project project})))


(defn recompute-initial-scenarios
  [initial-scenarios scenarios-component projects2]
  (dorun (map (partial recompute-initial-scenario projects2 scenarios-component) initial-scenarios)))


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
            scenarios-component     (:planwise.component/scenarios system)
            database                (:spec (second (ig/find-derived-1 system :duct.database/sql)))
            initial-scenarios       (jdbc/query database ["SELECT id, label, \"project-id\" FROM scenarios WHERE label = 'initial'"])
            scenarios               (jdbc/query database ["SELECT id, label, \"project-id\" FROM scenarios WHERE label <> 'initial'"])
            scenarios               (group-by :project-id scenarios)]

        (recompute-initial-scenarios initial-scenarios scenarios-component projects2)
        (recompute-scenario-groups scenarios scenarios-component projects2))

      (finally
        (println "Shutting down system")
        (ig/halt! system)))))
