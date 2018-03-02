(ns planwise.config
  (:require [duct.core.env :as env]))

(defmethod env/coerce 'Bool [x _]
  (Boolean/valueOf x))
