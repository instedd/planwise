(defproject planwise "0.5.0-SNAPSHOT"
  :description "Facility Planner"
  :url "http://github.com/instedd/planwise"
  :min-lein-version "2.0.0"
  :dependencies [; Base infrastructure
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [org.clojure/core.async "0.2.385"]
                 [com.stuartsierra/component "0.3.1"]
                 [prismatic/schema "1.1.3"]
                 [duct "0.6.1"]

                 ; Web server and routing
                 [compojure "1.5.0"
                  :exclusions [commons-codec]]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.2.0"]
                 [ring/ring-json "0.4.0"]
                 [ring-jetty-component "0.3.1"]
                 [ring-webjars "0.1.1"]
                 [amalloy/ring-gzip-middleware "0.1.3"]

                 ; Security
                 [buddy "1.0.0"]
                 [org.openid4java/openid4java "1.0.0"
                  :exclusions [commons-logging
                               org.apache.httpcomponents/httpclient]]
                 [oauthentic "1.0.1"
                  :exclusions [org.apache.httpcomponents/httpclient]]

                 ; Configuration
                 [environ "1.0.3"]
                 [meta-merge "0.1.1"]

                 ; Logging
                 [com.taoensso/timbre "4.5.1"]
                 [com.fzakaria/slf4j-timbre "0.3.2"]
                 [org.slf4j/log4j-over-slf4j "1.7.14"]
                 [org.slf4j/jul-to-slf4j "1.7.14"]
                 [org.slf4j/jcl-over-slf4j "1.7.14"]

                 ; Rendering and data handling
                 [hiccup "1.0.5"]
                 [cheshire "5.6.3"]
                 [clj-time "0.12.0"]
                 [reduce-fsm "0.1.4"]

                 ; Client infrastructure
                 [reagent "0.5.1"
                  :exclusions [org.clojure/tools.reader]]
                 [reagent-forms "0.5.23"]
                 [reagent-utils "0.1.8"]
                 [re-frame "0.7.0"]
                 [re-com "0.8.3"]
                 [cljs-ajax "0.5.4"
                  :exclusions [commons-codec]]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.1.7"
                  :exclusions [org.clojure/tools.reader]]

                 ; Client assets and components
                 [org.webjars/normalize.css "3.0.2"]
                 [org.webjars/leaflet "0.7.7"]

                 ; Database access
                 [duct/hikaricp-component "0.1.0"
                  :exclusions [org.slf4j/slf4j-nop]]
                 [duct/ragtime-component "0.1.4"]
                 [org.postgresql/postgresql "9.4.1208"]
                 [net.postgis/postgis-jdbc "2.1.7.2"
                  :exclusions [postgresql
                               ch.qos.logback/logback-classic
                               ch.qos.logback/logback-core]]
                 [com.layerware/hugsql "0.4.7"]

                 ; Misc
                 [digest "1.4.4"]]

  :plugins [[lein-environ "1.0.3"]
            [lein-cljsbuild "1.1.2"]
            [lein-sass "0.3.7"
             :exclusions
             [org.apache.commons/commons-compress
              org.codehaus.plexus/plexus-utils]]]
  :main ^:skip-aot planwise.main
  :target-path "target/%s/"
  :resource-paths ["resources" "target/cljsbuild" "target/sass"]
  :prep-tasks [["javac"] ["cljsbuild" "once"] ["sass" "once"] ["compile"]]
  :sass
  {:src "resources/sass"
   :output-directory "target/sass/planwise/public/css"
   :style :compressed}
  :cljsbuild
  {:builds
   {:main {:jar true
           :source-paths ["src" "prod"]
           :compiler {:output-to "target/cljsbuild/planwise/public/js/main.js"
                      :optimizations :advanced
                      :externs ["prod/externs.js"]}}}}
  :aliases {"run-task"     ["with-profile" "+repl" "run" "-m"]
            "setup"        ["run-task" "dev.tasks/setup"]
            "import-sites" ["run-task" "planwise.tasks.import-sites"]
            "migrate"      ["run-task" "planwise.tasks.db" "migrate"]
            "rollback"     ["run-task" "planwise.tasks.db" "rollback"]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :repl {:resource-paths ^:replace ["resources" "target/figwheel" "target/sass-repl"]
          :prep-tasks     ^:replace [["javac"] ["compile"]]}
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [; Framework
                                  [duct/generate "0.6.1"
                                   :exclusions [org.codehaus.plexus/plexus-utils]]
                                  [duct/figwheel-component "0.3.2"
                                   :exclusions [org.clojure/data.priority-map
                                                org.clojure/core.async]]

                                  ; REPL tools
                                  [reloaded.repl "0.2.1"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [com.cemerick/piggieback "0.2.1"]

                                  ; Testing libraries
                                  [eftest "0.1.1"]
                                  [kerodon "0.7.0"]
                                  [fixtures-component "0.4.2"
                                   :exclusions [org.clojure/java.jdbc]]
                                  [ring/ring-mock "0.3.0"]

                                  ; Helpers
                                  [hawk "0.2.10"]
                                  [binaryage/devtools "0.6.1"]]

                   :source-paths ["dev"]
                   :repl-options {:init-ns user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :env {:port "3000"}}
   :project/test  {}})
