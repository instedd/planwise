(ns planwise.client.config
  (:require [planwise.model.project]))

(def global-config
  (aget js/window "_CONFIG"))

(def resourcemap-url
  (aget global-config "resourcemap-url"))

(def jwe-token
  (aget global-config "jwe-token"))

(def user-email
  (aget global-config "identity"))

(def mapserver-url
  (aget global-config "mapserver-url"))

(def app-version
  (aget global-config "app-version"))

(def facilities-default-capacity
  (aget global-config "facilities-default-capacity"))

(def calculate-demand
  (aget global-config "calculate-demand"))
