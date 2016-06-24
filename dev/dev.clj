(ns dev
  (:refer-clojure :exclude [test])
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [duct.generate :as gen]
            [meta-merge.core :refer [meta-merge]]
            [reloaded.repl :refer [system init start stop go reset]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [duct.component.figwheel :as figwheel]
            [dev.tasks :refer :all]
            [dev.sass :as sass]
            [dev.auto :as auto]
            [planwise.config :as config]
            [planwise.system :as system]))

(timbre/set-level! :info)

(def dev-config
  {:app {:middleware [wrap-stacktrace]}
   :figwheel
   {:css-dirs ["resources/planwise/public/css"
               "target/sass-repl"]
    :builds   [{:source-paths ["src" "dev"]
                :build-options
                {:optimizations :none
                 :main "cljs.user"
                 :asset-path "/js"
                 :output-to  "target/figwheel/planwise/public/js/main.js"
                 :output-dir "target/figwheel/planwise/public/js"
                 :source-map true
                 :source-map-path "/js"}}]}
   :sass
   {:input "resources/sass/site.scss"
    :output "target/sass-repl/planwise/public/css/site.css"
    :options ["-m"]}})

(def config
  (meta-merge config/defaults
              config/environ
              dev-config))

(defn new-system []
  (into (system/new-system config)
        {:sass (sass/sass-compiler (:sass config))
         :figwheel (component/using
                    (figwheel/server (:figwheel config))
                    [:sass])
         :auto (component/using
                (auto/auto-builder {:enabled true})
                [:figwheel :sass])}))

(when (io/resource "local.clj")
  (load "local"))

(defn db []
  (:spec (:db system)))

(gen/set-ns-prefix 'planwise)

(reloaded.repl/set-init! new-system)
