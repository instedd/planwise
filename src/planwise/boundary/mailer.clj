(ns planwise.boundary.mailer
  (:require [planwise.component.mailer :as service]))

(defprotocol Mailer
  "Mail sender"

  (send-mail [service args]
    "Sends an email based on the specified args: `:from`, `:to`, `:cc`,
     `:subject` and `:body`. Refer to https://github.com/drewr/postal
     fore more information."))

;; Reference implementation

(extend-protocol Mailer
  planwise.component.mailer.MailerService
  (send-mail [service args]
    (service/send-mail service args)))
