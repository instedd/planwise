(ns planwise.component.resmap
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clj-http.client :as http]))

(timbre/refer-timbre)

(defrecord ResmapClient [url])

(defn resmap-client
  [config]
  (map->ResmapClient config))


(defn resmap-url
  ([service]
   (resmap-url service nil))
  ([service path]
   (let [path (if (str/starts-with? path "/") path (str "/" path))
         base-url (:url service)
         base-url (if (str/ends-with? base-url "/")
                    (.substring base-url 0 (dec (.length base-url)))
                    base-url)]
     (str base-url path))))

(defn- auth-headers
  [token]
  (if-not (str/blank? token)
    {"Authorization" (str "Bearer " token)}
    {}))

(defn list-collections
  ([service]
   (list-collections service nil))
  ([service token]
   (let [url (resmap-url service "/api/collections.json")
         response (http/get url {:headers (auth-headers token)
                                 :throw-exceptions false})]
     (if (= 200 (:status response))
       (json/parse-string (:body response))
       (do
         (warn "Failure to retrive Resourcemap collections:"
               (get-in response [:headers "status"]))
         [])))))
