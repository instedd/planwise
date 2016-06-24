(ns dev.auto
  (:require [com.stuartsierra.component :as c]
            [hawk.core :as hawk]
            [clojure.java.io :as io]
            [figwheel-sidecar.components.cljs-autobuild :as fig-auto]
            [duct.component.figwheel :as figwheel]
            [dev.sass :as sass]))

(defn compute-css-paths [sass]
  (let [path (-> (:input sass)
                 (io/file)
                 (.getParent))]
    [path]))

(defn compute-cljs-paths [figwheel]
  (mapcat fig-auto/source-paths-that-affect-build (:builds figwheel)))

(defrecord AutoBuilder [enabled figwheel sass]

  c/Lifecycle
  (start [component]
    (if enabled
      (let [css-paths (compute-css-paths sass)
            cljs-paths (compute-cljs-paths figwheel)
            watch (hawk/watch! [{:paths css-paths
                                 :filter hawk/file?
                                 :handler (fn [ctx e]
                                            (sass/rebuild sass)
                                            (figwheel/refresh-css figwheel)
                                            ctx)}
                                {:paths cljs-paths
                                 :filter hawk/file?
                                 :handler (fn [ctx e]
                                            (figwheel/build-cljs figwheel)
                                            ctx)}])]
        (println "Starting auto builder")
        (assoc component
               :watch watch))
      component))

  (stop [component]
    (when-let [watch (:watch component)]
      (hawk/stop! watch))
    (assoc component
           :watch nil)))

(defn auto-builder [config]
  (map->AutoBuilder config))
