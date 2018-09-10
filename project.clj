(defproject planwise "0.8.0-SNAPSHOT"
  :description "Facility Planner"
  :url "http://github.com/instedd/planwise"
  :min-lein-version "2.0.0"
  :dependencies [; Base infrastructure
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.238"]
                 [org.clojure/core.async "0.4.474"]
                 [prismatic/schema "1.1.7"]

                 [duct/core "0.6.2"]
                 [duct/module.logging "0.3.1"]
                 [duct/module.web "0.6.4"
                  :exclusions [org.slf4j/slf4j-nop]]
                 [duct/module.cljs "0.3.2"]
                 [duct/module.sql "0.4.2"]
                 [duct/compiler.sass "0.1.1"]

                 ; Web server and routing
                 [compojure "1.6.0"]
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
                 [re-com "2.1.0"]
                 [day8.re-frame/http-fx "0.1.5"]
                 [crate "0.2.4"]
                 [cljs-ajax "0.7.3"
                  :exclusions [commons-codec]]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.4"
                  :exclusions [org.clojure/tools.reader]]

                 ; Client assets and components
                 [org.webjars/normalize.css "5.0.0"]
                 [org.webjars/leaflet "1.3.1"]

                 ; Database access
                 [org.clojure/java.jdbc "0.7.5"]
                 [org.postgresql/postgresql "42.2.1"]
                 [net.postgis/postgis-jdbc "2.2.1"
                  :exclusions [postgresql
                               org.postgresql/postgresql
                               ch.qos.logback/logback-classic
                               ch.qos.logback/logback-core]]
                 [com.layerware/hugsql "0.4.7"]

                 ; Misc
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/data.json "0.2.4"]
                 [com.draines/postal "2.0.0"]
                 [funcool/cuerdas "2.0.3"]
                 [org.gdal/gdal "2.1.0"]
                 [org.clojure/math.combinatorics "0.1.4"]
                 [net.mintern/primitive "1.3"]]

  :plugins [[duct/lein-duct "0.10.6"]
            [lein-doo "0.1.10"]
            [lein-cljfmt "0.5.7"]]

  :cljfmt {:remove-consecutive-blank-lines? false}

  :resource-paths     ["resources" "target/resources"]
  :java-source-paths  ["java"]
  :target-path        "target/%s/"
  :main               ^:skip-aot planwise.main

  :prep-tasks         ["javac"
                       "compile"
                       ["run" ":duct/compiler"]]
  :jar-exclusions     [#"^svg/icons/.*" #"^sass/.*" #".*DS_Store$" #"^planwise/public/js/.*/.*"]
  :uberjar-exclusions [#"^svg/icons/.*" #"^sass/.*" #".*DS_Store$" #"^planwise/public/js/.*/.*"]
  :uberjar-name       "planwise-standalone.jar"

  :aliases {"migrate"               ["with-profile" "+repl" "run" ":duct/migrator"]
            "build-icons"           ["with-profile" "+repl" "run" "-m" "planwise.tasks.build-icons"]
            "preprocess-facilities" ["with-profile" "+repl" "run" "-m" "planwise.tasks.preprocess-facilities"]
            "import-population"     ["with-profile" "+repl" "run" "-m" "planwise.tasks.import-population"]
            "test-cljs"             ["with-profile" "+test" "doo"]}

  ;; For lein-doo to compile the client code for testing
  ;; Development and release CLJS build configuration are in dev.edn and prod.edn
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to     "target/cljsbuild/test/main.js"
                                   :output-dir    "target/cljsbuild/test"
                                   :main          planwise.client.test-runner
                                   :verbose       false
                                   :optimizations :none}}
                       {:id "test-nodejs"
                        :source-paths ["src" "test"]
                        :compiler {:output-to     "target/cljsbuild/test/main.js"
                                   :output-dir    "target/cljsbuild/test"
                                   :target        :nodejs
                                   :main          planwise.client.test-runner
                                   :verbose       false
                                   :optimizations :none}}]}

  :doo {:build   "test-nodejs"
        :alias   {:default [:node]}}

  :profiles
  {:dev           [:project/dev  :profiles/dev]
   :test          [:project/test :profiles/test]
   :repl          {:prep-tasks   ^:replace ["javac" "compile"]
                   :repl-options {:init-ns user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                                  :host "0.0.0.0"
                                  :port 47480}}
   :uberjar       {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [; Framework
                                  [figwheel-sidecar "0.5.14"]
                                  [ring/ring-devel "1.6.3"]

                                  ; REPL tools
                                  [org.clojure/tools.namespace "0.3.0-alpha4"]
                                  [integrant/repl "0.2.0"]
                                  [virgil "0.1.8"]

                                  ; Testing libraries
                                  [eftest "0.4.3"
                                   :exclusions [io.aviso/pretty]]
                                  [kerodon "0.9.0"]
                                  [ring/ring-mock "0.3.0"]
                                  [com.gearswithingears/shrubbery "0.4.1"]

                                  ; Helpers
                                  [day8.re-frame/re-frame-10x "0.2.0"]
                                  [binaryage/devtools "0.9.9"]]

                   :source-paths   ["dev/src"]
                   :resource-paths ["dev/resources" "test/resources"]}
   :project/test  {:prep-tasks     ^:replace ["javac" "compile"]
                   :resource-paths ["test/resources"]}})
