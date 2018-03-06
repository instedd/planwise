(ns planwise.component.mailer
  (:require [planwise.boundary.mailer :as boundary]
            [integrant.core :as ig]
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

(defrecord MailerService [config]

  ;; Reference implementation

  boundary/Mailer
  (send-mail [service args]
    (send-mail service args)))

(defmethod ig/init-key :planwise.component/mailer
  [_ config]
  (map->MailerService config))
