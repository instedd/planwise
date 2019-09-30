(ns build
  (:require [build.sass :as sass]))

(defn sass
  []
  (sass/build-all
   {:source-paths  ["resources/sass"]
    :output-path   "target/planwise/public/css"
    :include-paths ["node_modules"]
    :output-style  :nested
    :source-map?   true}))
