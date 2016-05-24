(ns viewer.db
  (:require [config.core :refer [env]]))

(def db (env :database-url))
