(ns planwise.util.ring
  (:require [ring.util.request :refer [request-url]])
  (:import [java.net URL MalformedURLException]))

(defn- url? [^String s]
  (try (URL. s) true
       (catch MalformedURLException _ false)))

(defn absolute-url [location request]
  (if (url? location)
    location
    (let [url (URL. (request-url request))]
      (str (URL. url location)))))
