(ns planwise.config
  (:require [duct.core.env :as env]
            ;; Force load SASS compiler component for key derivation declaration
            [duct.compiler.sass]))

(defmethod env/coerce 'Bool [x _]
  (Boolean/valueOf x))
