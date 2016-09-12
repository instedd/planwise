(ns planwise.util.ring
  (:require [ring.util.request :refer [request-url]]
            [planwise.model.ident :as ident]
            [taoensso.timbre :as timbre])
  (:import [java.net URL MalformedURLException]))

(timbre/refer-timbre)

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

(defn wrap-debug-request
  "Middleware to print each request to the console"
  [handler]
  (fn [request]
    (println (str "\n\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-="))
    (clojure.pprint/pprint request)
    (handler request)))

(def filtered-params #{"password"})

(defn- filter-params
  [params]
  (into {} (map
            (fn [[k v]]
              [k (if (filtered-params (name k)) "[FILTERED]" v)])
            params)))

(defn wrap-log-request
  "Middleware to log each HTTP request in a single line"
  ([handler options]
   (let [level (:level options :info)
         exclude-uris (:exclude-uris options)]
     (fn [request]
       (let [uri (:uri request "")
             params (:params request)]
         (when-not (and (some? exclude-uris)
                        (re-matches exclude-uris uri))
           (log level "Starting request:"
                (-> (:request-method request) name .toUpperCase)
                (:uri request)
                (or (:query-string request) ""))
           (when (seq params)
             (log level "...  with params:" (filter-params params)))))
       (handler request)))))
