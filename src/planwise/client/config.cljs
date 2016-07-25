(ns planwise.client.config)

(def global-config
  (aget js/window "_CONFIG"))

(def resourcemap-url
  (aget global-config "resourcemap-url"))

(def jwe-token
  (aget global-config "jwe-token"))

(def user-email
  (aget global-config "identity"))
