(ns planwise.boundary.mailer)

(defprotocol Mailer
  "Mail sender"

  (send-mail [service args]
    "Sends an email based on the specified args: `:from`, `:to`, `:cc`,
     `:subject` and `:body`. Refer to https://github.com/drewr/postal
     fore more information."))

