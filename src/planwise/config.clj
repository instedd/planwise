(ns planwise.config
  (:require [duct.core.env :as env]
            [integrant.core :as ig]
            ;; Force load SASS compiler component for key derivation declaration
            [duct.compiler.sass]
            [planwise.sass]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [planwise.model.project]))

(defmethod env/coerce 'Bool [x _]
  (Boolean/valueOf x))

(def app-version
  (or (some-> (io/resource "planwise/version")
              slurp
              str/trim-newline
              str/trim)
      "development"))

(defmethod ig/init-key :planwise.config/npm-deps [_ options] options)
