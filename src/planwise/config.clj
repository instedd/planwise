(ns planwise.config
  (:require [duct.core.env :as env]
            ;; Force load SASS compiler component for key derivation declaration
            [duct.compiler.sass]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defmethod env/coerce 'Bool [x _]
  (Boolean/valueOf x))

(def app-version
  (or (some-> (io/resource "planwise/version")
              slurp
              str/trim-newline
              str/trim)
      "development"))
