(ns planwise.util.ring
  (:require [ring.util.request :refer [request-url]]
            [planwise.model.ident :as ident])
  (:import [java.net URL MalformedURLException]))

(defn- url? [^String s]
  (try (URL. s) true
       (catch MalformedURLException _ false)))

(defn absolute-url [location request]
  (if (url? location)
    location
    (let [url (URL. (request-url request))]
      (str (URL. url location)))))

(defn request-ident
  [request]
  (:identity request))

(defn request-user-id
  [request]
  (-> (request-ident request)
      ident/user-id))

(defn request-user-email
  [request]
  (-> (request-ident request)
      ident/user-email))
