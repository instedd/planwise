(ns planwise.component.mailer
  (:require [com.stuartsierra.component :as component]
            [postal.core :as postal]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn- mock?
  [{config :config}]
  (:mock? config))

(defn- connection-params
  [{config :config}]
  (if (get-in config [:smtp :host])
    (:smtp config)))

(defn- default-params
  [{config :config}]
  {:from (:sender config)})

(defn send-mail
  [{config :config, :as service} params]
  (let [mail-params (merge (default-params service) params)]
    (info "Sending email: " mail-params)
    (if-not (mock? service)
      (let [result (if-let [conn (connection-params service)]
                      (postal/send-message conn mail-params)
                      (postal/send-message mail-params))]
        (= :SUCCESS (:error result)))
      true)))

(defrecord MailerService [config])

(defn mailer-service
  "Construct a Mailer Service component from config"
  [config]
  (map->MailerService {:config config}))
