(ns planwise.test-system
  (:require [com.stuartsierra.component :as component]
            [duct.component.ragtime :refer [ragtime migrate rollback]]
            [fixtures.adapters.jdbc :refer [jdbc-adapter]]
            [fixtures.component :refer [fixtures]]
            [meta-merge.core :refer [meta-merge]]
            [environ.core :refer [env]]))

;; Component to run the migrations on the test database automatically
(defrecord MigrationRunner [db ragtime]
  component/Lifecycle
  (start [component]
    (migrate (:ragtime component))
    component)
  (stop [component]
    component))

(defn migration-runner []
  (map->MigrationRunner {}))

(defmacro with-system
  "Execute body with the system in the first argument started and bound to the
  'system' symbol, and stop it after the execution is complete."
  [system & body]
  `(let [~'system (component/start ~system)]
     (try
       ~@body
       (finally
         (component/stop ~'system)))))

(def test-config
  {:db       {:uri (env :test-database-url)}
   :fixtures {:adapter jdbc-adapter
              :data []}})

(defn test-system
  "Construct a base test system with migrations (will auto-run when the system
  is started) and fixtures components."
  [config]
  (let [config (meta-merge test-config config)]
    (-> (component/system-map
         :db         {:spec (get-in config [:db :uri])}
         :ragtime    (ragtime {:resource-path "migrations"})
         :migrations (migration-runner)
         :fixtures   (fixtures (:fixtures config)))
        (component/system-using
         {:ragtime    [:db]
          :migrations [:db :ragtime]
          ; make fixtures depend on migrations so we ensure they are executed
          ; before loading the fixture data
          :fixtures   [:db :migrations]}))))
