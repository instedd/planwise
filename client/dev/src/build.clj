(ns build
  (:require [hawk.core :as hawk]
            [shadow.cljs.devtools.api :as shadow]
            [build.sass :as sass]))

(def sass-options
  {:source-paths  ["resources/sass"]
   :output-path   "target/planwise/public/css"
   :include-paths ["node_modules"]
   :output-style  :nested
   :source-map?   true})

(defn sass
  []
  (sass/build-all
   (merge sass-options {:output-style :nested})))

(defn sass-release
  []
  (sass/build-all
   (merge sass-options {:output-style :compressed})))

(defn watch-sass
  {:shadow/requires-server true}
  []
  (hawk/watch! [{:paths   ["resources/sass"]
                 :filter  hawk/file?
                 :handler (fn [ctx e] (sass))}]))

(defn watch
  {:shadow/requires-server true}
  []
  (sass)
  (watch-sass)
  (shadow/watch :app))

(defn build
  []
  (sass)
  (shadow/compile :app))

(defn release
  []
  (sass-release)
  (shadow/release :app))
