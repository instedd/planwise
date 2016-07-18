(ns planwise.component.resmap
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [planwise.component.auth :as auth])
  (:import [java.net URL]))

(timbre/refer-timbre)

(defrecord ResmapClient [url auth])

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

(defn auth-scope
  [service]
  (let [url (URL. (:url service))
        port (.getPort url)]
    (str "app=" (.getHost url) (when (> port 0) (str ":" port)))))

(defn- auth-headers
  [{token :token}]
  (if-not (str/blank? token)
    {"Authorization" (str "Bearer " token)}
    {}))

(defn map-collection
  [resmap-collection]
  {:id (:id resmap-collection)
   :name (:name resmap-collection)
   :count (:count resmap-collection)})

(defn list-collections
  ([service]
   (list-collections service nil))
  ([service token]
   (let [url (resmap-url service "/api/collections.json")
         response (http/get url {:headers (auth-headers token)
                                 :throw-exceptions false})]
     (if (= 200 (:status response))
       (->> (json/parse-string (:body response) true)
            (map map-collection))
       (do
         (warn "Failure to retrive Resourcemap collections:"
               (get-in response [:headers "status"]))
         [])))))

(defn get-collection-fields
  [service token coll-id]
  (let [url (resmap-url service (str "/api/collections/" coll-id "/fields.json"))
        response (http/get url {:headers (auth-headers token)
                                :throw-exceptions false})]
    (if (= 200 (:status response))
      (->> (json/parse-string (:body response) true)
           (mapcat (fn [layer] (:fields layer)))
           (map (fn [field] (select-keys field [:id :name :kind :config]))))
      (do
        (warn "Failure retrieving fields for Resourcemap collection" coll-id
              (get-in response [:headers "status"]))
        []))))

(defn list-user-collections
  [service user-ident]
  (let [scope (auth-scope service)
        auth (:auth service)
        token (auth/find-auth-token auth scope user-ident)]
    (info "Retrieving Resourcemap collections for user" (auth/get-email user-ident))
    (list-collections service token)))

(defn list-collection-fields
  [service user-ident coll-id]
  (let [scope (auth-scope service)
        auth (:auth service)
        token (auth/find-auth-token auth scope user-ident)]
    (info "Retrieving Resourcemap fields information for collection"
          coll-id "for user" (auth/get-email user-ident))
    (get-collection-fields service token coll-id)))

(defn authorised?
  [{:keys [auth] :as service} user-ident]
  (let [scope (auth-scope service)
        token (auth/find-auth-token auth scope user-ident)]
    (not (nil? token))))

