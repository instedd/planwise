(defproject planwise "0.8.0-SNAPSHOT"
  :description "Facility Planner"
  :url "http://github.com/instedd/planwise"
  :min-lein-version "2.0.0"
  :dependencies [; Base infrastructure
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.908"]
                 [org.clojure/core.async "0.4.474"]
                 [prismatic/schema "1.1.7"]

                 [duct/core "0.6.2"]
                 [duct/module.logging "0.3.1"]
                 [duct/module.web "0.6.4"]
                 [duct/module.cljs "0.3.2"]
                 [duct/module.sql "0.4.2"]

                 ; Web server and routing
                 [compojure "1.6.0"
                  :exclusions [commons-codec]]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-servlet "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [ring/ring-defaults "0.3.1"]
                 [ring/ring-json "0.4.0"]
                 [ring-webjars "0.2.0"]
                 [amalloy/ring-gzip-middleware "0.1.3"]

                 ; Security
                 [buddy "2.0.0"]
                 [org.openid4java/openid4java "1.0.0"
                  :exclusions [commons-logging
                               org.apache.httpcomponents/httpclient]]
                 [oauthentic "1.0.1"
                  :exclusions [org.apache.httpcomponents/httpclient
                               clj-http]]
                 [clj-http "3.7.0"]
                                        ; needed by oauthentic

                 ; Configuration
                 [environ "1.0.3"]
                 [meta-merge "0.1.1"]

                 ; Logging
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.8"]
                 [org.slf4j/log4j-over-slf4j "1.7.14"]
                 [org.slf4j/jul-to-slf4j "1.7.14"]
                 [org.slf4j/jcl-over-slf4j "1.7.14"]

                 ; Rendering and data handling
                 [hiccup "1.0.5"]
                 [cheshire "5.8.0"]
                 [clj-time "0.14.2"]
                 [reduce-fsm "0.1.4"]

                 ; Client infrastructure
                 [reagent "0.7.0"
                  :exclusions [org.clojure/tools.reader]]
                 [reagent-forms "0.5.36"]
                 [reagent-utils "0.3.0"]
                 [re-frame "0.10.5"]
                 [re-com "0.9.0"]
                 [day8.re-frame/http-fx "0.1.5"]
                 [cljs-ajax "0.7.3"
                  :exclusions [commons-codec]]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.1.7"
                  :exclusions [org.clojure/tools.reader]]

                 ; Client assets and components
                 [org.webjars/normalize.css "3.0.2"]
                 [org.webjars/leaflet "0.7.7"]

                 ; Database access
                 [org.postgresql/postgresql "42.2.1"]
                 [net.postgis/postgis-jdbc "2.2.1"
                  :exclusions [postgresql
                               org.postgresql/postgresql
                               ch.qos.logback/logback-classic
                               ch.qos.logback/logback-core]]
                 [com.layerware/hugsql "0.4.7"]

                 ; Misc
                 [digest "1.4.4"]
                 [com.draines/postal "2.0.0"]
                 [funcool/cuerdas "2.0.3"]]

  :plugins [[duct/lein-duct "0.10.6"]
            [lein-environ "1.0.3"]
            [lein-cljsbuild "1.1.5"]
            [lein-sass "0.3.7"
             :exclusions
             [org.apache.commons/commons-compress
              org.codehaus.plexus/plexus-utils]]]
  :main ^:skip-aot planwise.main
  :target-path "target/%s/"
  :resource-paths ["resources" "target/resources" "target/cljsbuild" "target/sass"]
  :prep-tasks ["javac"
               ["cljsbuild" "once"]
               ["sass" "once"]
               "compile"
               ["run" ":duct/compiler"]]
  :jar-exclusions [#"^svg/icons/.*" #"^sass/.*" #".*DS_Store$"]
  :uberjar-exclusions [#"^svg/icons/.*" #"^sass/.*" #".*DS_Store$"]
  :uberjar-name "planwise-standalone.jar"
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
                      :externs ["prod/planwise.externs.js"]}}}}
  :aliases {"run-task"              ["with-profile" "+repl" "run" "-m"]
            "migrate"               ["run-task" "planwise.tasks.db" "migrate"]
            "rollback"              ["run-task" "planwise.tasks.db" "rollback"]
            "build-icons"           ["run-task" "planwise.tasks.build-icons"]
            "preprocess-facilities" ["run-task" "planwise.tasks.preprocess-facilities"]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :repl {:prep-tasks     ^:replace ["javac" "compile"]
          :repl-options   {:init-ns user
                           :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                           :host "0.0.0.0"
                           :port 47480}}
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [; Framework
                                  [figwheel-sidecar "0.5.14"]
                                  [ring/ring-devel "1.6.3"]

                                  ; REPL tools
                                  [integrant/repl "0.2.0"]

                                  ; Testing libraries
                                  [eftest "0.4.3"]
                                  [kerodon "0.9.0"]
                                  [ring/ring-mock "0.3.0"]

                                  ; Helpers
                                  [day8.re-frame/re-frame-10x "0.2.0"]
                                  [hawk "0.2.10"]
                                  [binaryage/devtools "0.9.9"]]

                   :source-paths   ["dev/src"]
                   :resource-paths ["dev/resources"]
                   :env {:port "3000"
                         :database-url "jdbc:postgresql://localhost:5433/planwise?user=planwise&password=planwise"
                         :test-database-url "jdbc:postgresql://localhost:5433/planwise-test?user=planwise&password=planwise"
                         :guisso-url "https://login.instedd.org"}}
   :project/test  {}})
