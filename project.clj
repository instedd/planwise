(defproject planwise "0.1.0-SNAPSHOT"
  :description "Facility Planner"
  :url "http://github.com/instedd/planwise"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [com.stuartsierra/component "0.3.1"]
                 [compojure "1.5.0"]
                 [duct "0.6.1"]
                 [environ "1.0.3"]
                 [meta-merge "0.1.1"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.2.0"]
                 [ring-jetty-component "0.3.1"]
                 [ring-webjars "0.1.1"]
                 [com.taoensso/timbre "4.3.1"]
                 [com.fzakaria/slf4j-timbre "0.3.2"]
                 [hiccup "1.0.5"]
                 [org.webjars/normalize.css "3.0.2"]
                 [org.webjars/leaflet "0.7.7"]
                 [reagent "0.5.1"
                  :exclusions [org.clojure/tools.reader]]
                 [reagent-forms "0.5.23"]
                 [reagent-utils "0.1.8"]
                 [re-frame "0.7.0"]
                 [cljs-ajax "0.5.4"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.1.7"
                  :exclusions [org.clojure/tools.reader]]
                 [com.layerware/hugsql "0.4.7"]
                 [duct/ragtime-component "0.1.4"]
                 [duct/hikaricp-component "0.1.0"
                  :exclusions [org.slf4j/slf4j-nop]]
                 [org.postgresql/postgresql "9.4.1208"]]
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
            "import-sites" ["run-task" "dev.import-sites"]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :repl {:resource-paths ^:replace ["resources" "target/figwheel" "target/sass-repl"]
          :prep-tasks     ^:replace [["javac"] ["compile"]]}
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [[duct/generate "0.6.1"]
                                  [reloaded.repl "0.2.1"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [eftest "0.1.1"]
                                  [kerodon "0.7.0"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [duct/figwheel-component "0.3.2"]
                                  [figwheel "0.5.0-6"]
                                  [binaryage/devtools "0.6.1"]
                                  [hawk "0.2.10"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :env {:port "3000"}}
   :project/test  {}})
