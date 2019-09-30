(ns planwise.client.config
  (:require [planwise.model.project]))

(def global-config
  (aget js/window "_CONFIG"))

(def jwe-token
  (aget global-config "jwe-token"))

(def user-email
  (aget global-config "identity"))

(def mapserver-url
  (aget global-config "mapserver-url"))

(def app-version
  (aget global-config "app-version"))

(def intercom-app-id
  (aget global-config "intercom-app-id"))
